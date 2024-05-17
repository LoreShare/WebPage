package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.sample.probes.LivenessHandler;
import io.javaoperatorsdk.operator.sample.probes.StartupHandler;

import com.sun.net.httpserver.HttpServer;

/**
 * 入口类
 */
public class WebPageOperator {
  /**
   * 环境变量Key
   */
  public static final String WEBPAGE_RECONCILER_ENV = "WEBPAGE_RECONCILER";

  /**
   * 环境变量Value,用于匹配,决定注册哪个调和器
   */
  public static final String WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE = "classic";
  public static final String WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE = "managed";

  /**
   * 日志
   */
  private static final Logger log = LoggerFactory.getLogger(WebPageOperator.class);

  /**
   * 入口函数
   * 通过环境变量注册不同的调和器
   * 他们通过不同的实现方式，实现了相同的逻辑
   */
  public static void main(String[] args) throws IOException {
    log.info("WebServer Operator starting!");

    //构造K8S客户端
    //他的实现类是io.fabric8.kubernetes.client.impl.KubernetesClientImpl
    //这是一个线程安全的类
    KubernetesClient client = new KubernetesClientBuilder().build();

    //创建了Operator
    //过期问题可看源码,里面写了替换方式
    //这里的两个参数
    //1.一个是K8S的客户端,猜测Operator也是通过这个来和K8S交互的
    //2.第二个参数是Consumer
    //  2.1 是用作初始化ConfigurationServiceOverrider,可以通过传入Consumer来实现自定义的初始化
    //  2.2 ConfigurationServiceOverrider是用于构造ConfigurationService
    //  2.3 ConfigurationService是用于初始化Operator
    //  2.4 反过来看的话,ConfigurationServiceOverrider和他的名称一样,是用作覆盖ConfigurationService的配置
    //  2.5 传入的Consumer从它的字面意思来看,是用作在启动期间发生错误是否停止的
    //3.Operator有四个参数
    //  3.1 ControllerManager: 用于管理各种 controller
    //  3.2 LeaderElectionManager: 用于处理在分布式环境下的 leader 选举
    //  3.3 ConfigurationService: 用于管理 Operator 的配置信息
    //  3.4 boolean started: 用于标识 Operator 是否已经启动
    Operator operator = new Operator(client, o -> o.withStopOnInformerErrorDuringStartup(false));

    //获取环境变量,我们没有设置,可以在DockerFile或者K8S编排文件中设置
    String reconcilerEnvVar = System.getenv(WEBPAGE_RECONCILER_ENV);
    //根据环境变量,注册不同的调和器
    //我们做的话,只要写一种就好了,这里也只是注册了一种,他们的效果是一样的,只是实现方式有些区别
    if (WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE.equals(reconcilerEnvVar)) {
      //低级API
      operator.register(new WebPageReconciler(client));
    } else if (WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE.equals(reconcilerEnvVar)) {
      //使用管理的依赖资源
      operator.register(new WebPageManagedDependentsReconciler());
    } else {
      //使用独立的依赖资源,因为不配环境变量的话,默认就是这个,我们先看这个
      operator.register(new WebPageStandaloneDependentsReconciler());
    }
    //启动Operator
    operator.start();

    //创建了一个HttpServer,端口号为8080,作用是作为探针接口,重启失败的Operator
    //这个应该写在编排文件中
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/startup", new StartupHandler(operator));
    server.createContext("/healthz", new LivenessHandler(operator));
    server.setExecutor(null);
    server.start();
  }
}
