package io.javaoperatorsdk.operator.sample;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimited;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.createStatus;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.Utils.handleError;
import static io.javaoperatorsdk.operator.sample.Utils.isValidHtml;
import static io.javaoperatorsdk.operator.sample.Utils.makeDesiredIngress;
import static io.javaoperatorsdk.operator.sample.Utils.serviceName;
import static io.javaoperatorsdk.operator.sample.Utils.setInvalidHtmlErrorMessage;
import static io.javaoperatorsdk.operator.sample.Utils.simulateErrorIfRequested;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

/**
 * 使用low level api实现
 * "@RateLimited": 3 秒内最多进行 2 次调和
 * "@ControllerConfiguration": 标识这个Controller的配置类
 */
@RateLimited(maxReconciliations = 2, within = 3)
@ControllerConfiguration
public class WebPageReconciler implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  /**
   * ConfigMap的Key
   */
  public static final String INDEX_HTML = "index.html";

  /**
   * 记录日志
   */
  private static final Logger log = LoggerFactory.getLogger(WebPageReconciler.class);

  /**
   * K8S客户端
   */
  private final KubernetesClient kubernetesClient;

  /**
   * 这个构造方法会自动注入K8S客户端吗
   * @param kubernetesClient K8S客户端
   */
  public WebPageReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  /**
   * 准备事件源
   * 猜测这里应该要注意这个,withLabelSelector(SELECTOR),这代表资源是由我们的Operator管理的,要取一个唯一的标签名称
   * @param context a {@link EventSourceContext} 提供获取事件源有效信息的方法
   * @return 事件源
   */
  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    var configMapEventSource = new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context).withLabelSelector(SELECTOR).build(), context);
    var deploymentEventSource = new InformerEventSource<>(InformerConfiguration.from(Deployment.class, context).withLabelSelector(SELECTOR).build(), context);
    var serviceEventSource = new InformerEventSource<>(InformerConfiguration.from(Service.class, context).withLabelSelector(SELECTOR).build(), context);
    var ingressEventSource = new InformerEventSource<>(InformerConfiguration.from(Ingress.class, context).withLabelSelector(SELECTOR).build(), context);
    return EventSourceInitializer.nameEventSources(configMapEventSource, deploymentEventSource, serviceEventSource, ingressEventSource);
  }

  /**
   * 具体的调和函数
   * @param webPage the resource that has been created or updated
   * @param context the context with which the operation is executed
   * @return UpdateControl 用作更新CR的
   * @throws Exception 异常
   */
  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) throws Exception {

    log.info("Reconciling web page: {}", webPage);

    //如果演示需要,模拟错误的情况
    simulateErrorIfRequested(webPage);

    //检查CR是否合法
    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    //namespace名称
    String ns = webPage.getMetadata().getNamespace();
    //configmap名称
    String configMapName = configMapName(webPage);
    //deployment名称
    String deploymentName = deploymentName(webPage);

    //根据传入的CR解析出要创建的资源
    ConfigMap desiredHtmlConfigMap = makeDesiredHtmlConfigMap(ns, configMapName, webPage);
    Deployment desiredDeployment = makeDesiredDeployment(webPage, deploymentName, ns, configMapName);
    Service desiredService = makeDesiredService(webPage, ns, desiredDeployment);

    //对比已经存在的资源和要创建的资源,判断是否需要进行调和
    var previousConfigMap = context.getSecondaryResource(ConfigMap.class).orElse(null);
    if (!match(desiredHtmlConfigMap, previousConfigMap)) {
      log.info("Creating or updating ConfigMap {} in {}", desiredHtmlConfigMap.getMetadata().getName(), ns);
      kubernetesClient.configMaps().inNamespace(ns).resource(desiredHtmlConfigMap).createOr(Replaceable::update);
    }

    var existingDeployment = context.getSecondaryResource(Deployment.class).orElse(null);
    if (!match(desiredDeployment, existingDeployment)) {
      log.info("Creating or updating Deployment {} in {}", desiredDeployment.getMetadata().getName(), ns);
      kubernetesClient.apps().deployments().inNamespace(ns).resource(desiredDeployment).createOr(Replaceable::update);
    }

    var existingService = context.getSecondaryResource(Service.class).orElse(null);
    if (!match(desiredService, existingService)) {
      log.info("Creating or updating Deployment {} in {}", desiredDeployment.getMetadata().getName(), ns);
      kubernetesClient.services().inNamespace(ns).resource(desiredService).createOr(Replaceable::update);
    }

    var existingIngress = context.getSecondaryResource(Ingress.class);
    if (Boolean.TRUE.equals(webPage.getSpec().getExposed())) {
      var desiredIngress = makeDesiredIngress(webPage);
      if (existingIngress.isEmpty() || !match(desiredIngress, existingIngress.get())) {
        kubernetesClient.resource(desiredIngress).inNamespace(ns).createOr(Replaceable::update);
      }
    } else {
      existingIngress.ifPresent(ingress -> kubernetesClient.resource(ingress).delete());
    }

    //更新Pod,我测试下来,如果ConfigMap发生改变,但因为ConfigMap的名称没有改变,所以部署也不会改变,那Pod就不会更新
    //但是看这里,可能是存在自动更新的方式
    //注意，这并非必要操作，最终挂载的配置映射会被更新
    //只是这种方式更快，对于演示目的很方便
    //https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#mounted-configmaps-are-updated-automatically
    if (previousConfigMap != null && !StringUtils.equals(previousConfigMap.getData().get(INDEX_HTML), desiredHtmlConfigMap.getData().get(INDEX_HTML))) {
      log.info("Restarting pods because HTML has changed in {}", ns);
      kubernetesClient.pods().inNamespace(ns).withLabel("app", deploymentName(webPage)).delete();
    }

    //设置CR的状态
    webPage.setStatus(createStatus(desiredHtmlConfigMap.getMetadata().getName()));

    //更新CR
    return UpdateControl.patchStatus(webPage);
  }

  /**
   * 发生异常时的处理措施
   * @param resource 我们的CR
   * @param context 当前上下文
   * @param e 调和过程中抛出的异常
   * @return ErrorStatusUpdateControl: 猜测是用于更新CR状态的
   */
  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(WebPage resource, Context<WebPage> context, Exception e) {
    return handleError(resource, e);
  }

  private boolean match(Ingress desiredIngress, Ingress existingIngress) {
    String desiredServiceName = desiredIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName();
    String existingServiceName = existingIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName();
    return Objects.equals(desiredServiceName, existingServiceName);
  }

  private boolean match(Deployment desiredDeployment, Deployment deployment) {
    if (deployment == null) {
      return false;
    } else {
      return desiredDeployment.getSpec().getReplicas().equals(deployment.getSpec().getReplicas()) &&
          desiredDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage().equals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }
  }

  private boolean match(Service desiredService, Service service) {
    if (service == null) {
      return false;
    }
    return desiredService.getSpec().getSelector().equals(service.getSpec().getSelector());
  }

  private boolean match(ConfigMap desiredHtmlConfigMap, ConfigMap existingConfigMap) {
    if (existingConfigMap == null) {
      return false;
    } else {
      return desiredHtmlConfigMap.getData().equals(existingConfigMap.getData());
    }
  }

  private Service makeDesiredService(WebPage webPage, String ns, Deployment desiredDeployment) {
    Service desiredService = ReconcilerUtils.loadYaml(Service.class, getClass(), "service.yaml");
    desiredService.getMetadata().setName(serviceName(webPage));
    desiredService.getMetadata().setNamespace(ns);
    desiredService.getMetadata().setLabels(lowLevelLabel());
    desiredService.getSpec().setSelector(desiredDeployment.getSpec().getTemplate().getMetadata().getLabels());
    desiredService.addOwnerReference(webPage);
    return desiredService;
  }

  private Deployment makeDesiredDeployment(WebPage webPage, String deploymentName, String ns, String configMapName) {
    Deployment desiredDeployment = ReconcilerUtils.loadYaml(Deployment.class, getClass(), "deployment.yaml");
    desiredDeployment.getMetadata().setName(deploymentName);
    desiredDeployment.getMetadata().setNamespace(ns);
    desiredDeployment.getMetadata().setLabels(lowLevelLabel());
    desiredDeployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);
    desiredDeployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName);
    desiredDeployment
        .getSpec()
        .getTemplate()
        .getSpec()
        .getVolumes()
        .get(0)
        .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName).build());
    desiredDeployment.addOwnerReference(webPage);
    return desiredDeployment;
  }

  private ConfigMap makeDesiredHtmlConfigMap(String ns, String configMapName, WebPage webPage) {
    Map<String, String> data = new HashMap<>();
    data.put(INDEX_HTML, webPage.getSpec().getHtml());
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(configMapName)
                    .withNamespace(ns)
                    .withLabels(lowLevelLabel())
                    .build())
            .withData(data)
            .build();
    configMap.addOwnerReference(webPage);
    return configMap;
  }

  public static Map<String, String> lowLevelLabel() {
    Map<String, String> labels = new HashMap<>();
    labels.put(SELECTOR, "true");
    return labels;
  }
}
