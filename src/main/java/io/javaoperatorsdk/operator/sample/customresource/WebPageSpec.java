package io.javaoperatorsdk.operator.sample.customresource;

/**
 * CRD中的spec期望属性
 */
public class WebPageSpec {

  /**
   * 存放html
   */
  private String html;

  /**
   * 是否对外暴露,应该就是是否创建ingress
   */
  private Boolean exposed = false;

  public String getHtml() {
    return html;
  }

  public void setHtml(String html) {
    this.html = html;
  }

  public Boolean getExposed() {
    return exposed;
  }

  public WebPageSpec setExposed(Boolean exposed) {
    this.exposed = exposed;
    return this;
  }

  @Override
  public String toString() {
    return "WebPageSpec{" +
        "html='" + html + '\'' +
        '}';
  }
}
