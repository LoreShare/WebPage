package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 */
@ControllerConfiguration
public class WebPageStandaloneDependentsReconciler implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  private static final Logger log =
      LoggerFactory.getLogger(WebPageStandaloneDependentsReconciler.class);

  private Workflow<WebPage> workflow;

  public WebPageStandaloneDependentsReconciler() {
    workflow = createDependentResourcesAndWorkflow();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceInitializer.eventSourcesFromWorkflow(context, workflow);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    workflow.reconcile(webPage, context);

    webPage.setStatus(
        createStatus(
            context.getSecondaryResource(ConfigMap.class).orElseThrow().getMetadata().getName()));
    return UpdateControl.patchStatus(webPage);
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Workflow<WebPage> createDependentResourcesAndWorkflow() {
    var configMapDR = new ConfigMapDependentResource();
    var deploymentDR = new DeploymentDependentResource();
    var serviceDR = new ServiceDependentResource();
    var ingressDR = new IngressDependentResource();

    Arrays.asList(configMapDR, deploymentDR, serviceDR, ingressDR)
        .forEach(dr -> dr.configureWith(new KubernetesDependentResourceConfigBuilder()
            .withLabelSelector(SELECTOR + "=true").build()));

    return new WorkflowBuilder<WebPage>()
        .addDependentResource(configMapDR)
        .addDependentResource(deploymentDR)
        .addDependentResource(serviceDR)
        .addDependentResource(ingressDR)
        .withReconcilePrecondition(new ExposedIngressCondition())
        .build();
  }


}
