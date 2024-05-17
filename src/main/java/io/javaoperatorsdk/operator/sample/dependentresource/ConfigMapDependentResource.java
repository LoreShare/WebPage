package io.javaoperatorsdk.operator.sample.dependentresource;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.sample.WebPageReconciler;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

/**
 * CRUDKubernetesDependentResource:
 * 1.管理创建、读取和更新操作，并且应该在关联的主资源销毁时由 Kubernetes 自动进行垃圾回收的适配器类资源
 * 2.ConfigMap:管理的依赖资源类型
 * 3.WebPage:关联的主资源类型
 */
// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(labelSelector = SELECTOR)
public class ConfigMapDependentResource extends CRUDKubernetesDependentResource<ConfigMap, WebPage> {

  /**
   * 调用了父类的构造方法,直接将要管理的资源交给他就可以了马
   */
  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  /**
   * 就是写了如何通过CR来创建ConfigMap
   */
  @Override
  protected ConfigMap desired(WebPage webPage, Context<WebPage> context) {
    Map<String, String> data = new HashMap<>();
    data.put("index.html", webPage.getSpec().getHtml());

    Map<String, String> labels = WebPageReconciler.lowLevelLabel();

    ObjectMetaBuilder objectMetaBuilder = new ObjectMetaBuilder();
    //name
    objectMetaBuilder.withName(configMapName(webPage));
    //namespace
    objectMetaBuilder.withNamespace(webPage.getMetadata().getNamespace());
    //label
    objectMetaBuilder.withLabels(labels);
    ObjectMeta objectMeta = objectMetaBuilder.build();

    ConfigMapBuilder configMapBuilder = new ConfigMapBuilder();
    configMapBuilder.withMetadata(objectMeta);
    configMapBuilder.withData(data);

    return configMapBuilder.build();
  }
}
