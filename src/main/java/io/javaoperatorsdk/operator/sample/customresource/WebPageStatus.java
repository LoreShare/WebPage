package io.javaoperatorsdk.operator.sample.customresource;

import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;

/**
 * ObservedGenerationAwareStatus:用于支持生成感知性的状态子资源类扩展的辅助基类
 */
public class WebPageStatus extends ObservedGenerationAwareStatus {

  private String htmlConfigMap;

  private Boolean areWeGood;

  private String errorMessage;

  public String getHtmlConfigMap() {
    return htmlConfigMap;
  }

  public void setHtmlConfigMap(String htmlConfigMap) {
    this.htmlConfigMap = htmlConfigMap;
  }

  public Boolean getAreWeGood() {
    return areWeGood;
  }

  public void setAreWeGood(Boolean areWeGood) {
    this.areWeGood = areWeGood;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public WebPageStatus setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  @Override
  public String toString() {
    return "WebPageStatus{" +
        "htmlConfigMap='" + htmlConfigMap + '\'' +
        ", areWeGood='" + areWeGood + '\'' +
        ", errorMessage='" + errorMessage + '\'' +
        '}';
  }
}
