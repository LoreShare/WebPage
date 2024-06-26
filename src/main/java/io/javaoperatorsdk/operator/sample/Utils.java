package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.customresource.WebPageStatus;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;

public class Utils {

  private Utils() {}

  public static WebPageStatus createStatus(String configMapName) {
    WebPageStatus status = new WebPageStatus();
    status.setHtmlConfigMap(configMapName);
    status.setAreWeGood(true);
    status.setErrorMessage(null);
    return status;
  }

  public static String configMapName(WebPage nginx) {
    return nginx.getMetadata().getName() + "-html";
  }

  public static String deploymentName(WebPage nginx) {
    return nginx.getMetadata().getName();
  }

  public static String serviceName(WebPage webPage) {
    return webPage.getMetadata().getName();
  }

  public static ErrorStatusUpdateControl<WebPage> handleError(WebPage resource, Exception e) {
    resource.getStatus().setErrorMessage("Error: " + e.getMessage());
    return ErrorStatusUpdateControl.updateStatus(resource);
  }

  /**
   * 如果演示需要,模拟错误的情况
   * @param webPage CR
   * @throws ErrorSimulationException 异常
   */
  public static void simulateErrorIfRequested(WebPage webPage) throws ErrorSimulationException {
    if (webPage.getSpec().getHtml().contains("error")) {
      //如果演示需要,模拟错误的情况
      throw new ErrorSimulationException("Simulating error");
    }
  }

  public static boolean isValidHtml(WebPage webPage) {
    // very dummy html validation
    var lowerCaseHtml = webPage.getSpec().getHtml().toLowerCase();
    return lowerCaseHtml.contains("<html>") && lowerCaseHtml.contains("</html>");
  }

  public static WebPage setInvalidHtmlErrorMessage(WebPage webPage) {
    if (webPage.getStatus() == null) {
      webPage.setStatus(new WebPageStatus());
    }
    webPage.getStatus().setErrorMessage("Invalid html.");
    return webPage;
  }

  public static Ingress makeDesiredIngress(WebPage webPage) {
    Ingress ingress = loadYaml(Ingress.class, Utils.class, "ingress.yaml");
    ingress.getMetadata().setName(webPage.getMetadata().getName());
    ingress.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
        .getBackend().getService().setName(serviceName(webPage));
    return ingress;
  }
}
