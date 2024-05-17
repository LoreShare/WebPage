package io.javaoperatorsdk.operator.sample.customresource;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * 注解+类名 定义 GVK
 * 1.Namespaced代表他是命名空间级别的资源
 * 2.CustomResource:实现自定义资源类型的基类
 * 3.WebPageSpec+WebPageStatus:期望+状态
 */
@Group("sample.javaoperatorsdk")
@Version("v1")
public class WebPage extends CustomResource<WebPageSpec, WebPageStatus> implements Namespaced {

  @Override
  public String toString() {
    return "WebPage{" +
        "spec=" + spec +
        ", status=" + status +
        '}';
  }
}
