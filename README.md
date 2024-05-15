# WebPage Operator

这是一个简单的示例，演示了由 Operator 支持的自定义资源如何作为抽象层。此 Operator 将使用一个名为 WebPage 的资源，主要包含一个静态网页定义，并创建一个 NGINX 部署，该部署由一个保存 HTML 的 ConfigMap 支持。

以下是一个示例输入：

```yaml
apiVersion: "sample.javaoperatorsdk/v1"
kind: WebPage
metadata:
  name: mynginx-hello
spec:
  html: |
    <html>
      <head>
        <title>Webserver Operator</title>
      </head>
      <body>
        Hello World!
      </body>
    </html>
```

### 不同的实现方式

示例包含三种实现方式，展示了框架中可能的不同方法，最终的行为几乎相同：

- [低级API](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageDependentsWorkflowReconciler.java)
- [使用管理的依赖资源](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java)
- [使用独立的依赖资源](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageStandaloneDependentsReconciler.java)

### 尝试

尝试该 Operator 的最快方法是在本地运行它，同时连接到本地或远程的 Kubernetes 集群。

当您启动它时，它将使用您机器上当前的 kubectl 上下文连接到集群。



在运行之前，您必须通过运行以下命令将 CRD 安装到集群中： `kubectl apply -f target/classes/META-INF/fabric8/webpages.sample.javaoperatorsdk-v1.yml`。

CRD 是通过简单地向 `pom.xml` 文件中添加 `crd-generator-apt` 依赖项来自动生成的。



当 Operator 运行时，您可以创建一些 Webserver 自定义资源。您可以在 `k8s/webpage.yaml` 中找到一个示例自定义资源。您可以通过运行 `kubectl apply -f k8s/webpage.yaml` 来创建它。



在 Operator 捕获到新的 webserver 资源后（请参阅日志），它应该会在创建 webserver 资源的同一命名空间中创建 NGINX 服务器。要使用浏览器连接到服务器，您可以运行 `kubectl get service` 并查看 Operator 创建的服务。它应该配置了一个 NodePort。如果您运行的是单节点集群（例如 Docker for Mac 或 Minikube），您可以连接到此端口上的 VM 来访问页面。否则，您可以将服务更改为 LoadBalancer（例如在公共云上）。

您也可以尝试更改 `k8s/webpage.yaml` 中的 HTML 代码，然后再次运行 `kubectl apply -f k8s/webpage.yaml`。这应该会更新实际的 NGINX 部署，并应用新的配置。

请注意，有多个调解器实现会观察 `WebPage` 资源，其区别在于一个标签。当您创建一个新的 `WebPage` 资源时，请确保其标签与活动调解器的标签选择器匹配。



如果您希望 Operator 作为部署在集群中运行，请按以下步骤操作。

### 构建

为了将您的 Docker 构建指向 Minikube 的 Docker 注册表，请运行：

```
eval $(minikube docker-env)
```

您可以使用 `mvn jib:dockerBuild` 构建示例，这将生成一个 Docker 镜像，您可以将其推送到您选择的注册表中。JAR 文件是使用您本地的 Maven 和 JDK 构建的，然后将其复制到 Docker 镜像中。

### 部署

1. 部署 CRD: `kubectl apply -f target/classes/META-INF/fabric8/webpages.sample.javaoperatorsdk-v1.yml`
2. 部署 operator: `kubectl apply -f k8s/operator.yaml`
