package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.*;

import static io.javaoperatorsdk.operator.sample.Utils.*;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

/**
 * 使用独立的依赖资源
 * 1.Reconciler
 *   包含一个调和方法,接受一个自定义资源和上下文
 *   要求该方法的实现是幂等的,并且尽量使用它的返回值UpdateControl去更新自定义资源
 * 2.ErrorStatusHandler
 *   Reconciler可以实现这个接口，以便在抛出异常时更新状态子资源。在这种情况下，它的方法会自动调用
 *   方法调用的结果将用于对自定义资源进行状态更新,这总是一个子资源更新请求，因此不会对自定义资源本身（如元数据的规范）进行更新
 *   请注意，此更新请求还将产生一个事件，并且如果控制器不具有生成感知性，则会导致重新协调
 *   需要注意的是，此功能的范围仅限于调和程序的调和方法，因为在自定义资源被标记为删除之后,不应该对其进行更新
 * 3.EventSourceInitializer
 *   事件源相关的接口
 *   它允许 Reconciler 实现来注册提供的 EventSource
 * 4.@ControllerConfiguration这个注解要看一下,不知道是做什么的
 */
@ControllerConfiguration
public class WebPageStandaloneDependentsReconciler implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  //private static final Logger log = LoggerFactory.getLogger(WebPageStandaloneDependentsReconciler.class);

  /**
   * 看名称是和工作流相关的
   */
  private final Workflow<WebPage> workflow;

  /**
   * 构造函数,初始化上面的Workflow
   */
  public WebPageStandaloneDependentsReconciler() {
    workflow = createDependentResourcesAndWorkflow();
  }

  /**
   * 看起来是准备事件源,事件源是由上面的Workflow产生出来的
   */
  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceInitializer.eventSourcesFromWorkflow(context, workflow);
  }

  /**
   * 具体的调和函数
   */
  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    //如果演示需要,模拟错误的情况
    simulateErrorIfRequested(webPage);

    //如果HTML不合法
    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    //具体的调和,逻辑好像都是在源码里
    //我们只是定义了Workflow,然后在Workflow里指定了我们要部署的资源
    //我们未来要做的,难道只要自定义资源
    workflow.reconcile(webPage, context);

    //设置状态
    webPage.setStatus(
        createStatus(
            context.getSecondaryResource(ConfigMap.class).orElseThrow().getMetadata().getName()));

    //更新状态,这里和上面说的一样,是使用UpdateControl去更新的
    return UpdateControl.patchStatus(webPage);
  }

  /**
   * 应该是发生错误时需要做什么事情
   */
  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  /**
   * 用于创建依赖资源和工作流程
   * 在方法中，实例化了多个依赖资源，并为它们配置了标签选择器
   * 然后，使用这些依赖资源构建了一个工作流程，并指定了调解前提条件
   * @return Workflow
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Workflow<WebPage> createDependentResourcesAndWorkflow() {
    //这里定义的是什么,从名称来看,可能设计到相关资源的部署
    var configMapDR = new ConfigMapDependentResource();
    var deploymentDR = new DeploymentDependentResource();
    var serviceDR = new ServiceDependentResource();
    var ingressDR = new IngressDependentResource();

    //KubernetesDependentResourceConfigBuilder是什么
    //configureWith这个方法做了什么
    Arrays.asList(configMapDR, deploymentDR, serviceDR, ingressDR).forEach(dr -> dr.configureWith(new KubernetesDependentResourceConfigBuilder().withLabelSelector(SELECTOR + "=true").build()));

    //WorkflowBuilder和Workflow有什么作用
    return new WorkflowBuilder<WebPage>()
        .addDependentResource(configMapDR)
        .addDependentResource(deploymentDR)
        .addDependentResource(serviceDR)
        .addDependentResource(ingressDR)
        .withReconcilePrecondition(new ExposedIngressCondition())
        .build();
  }
}
