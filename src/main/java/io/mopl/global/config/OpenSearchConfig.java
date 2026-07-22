package io.mopl.global.config;

import org.opensearch.client.RestHighLevelClient;
import org.opensearch.data.client.orhlc.AbstractOpenSearchConfiguration;
import org.opensearch.data.client.orhlc.ClientConfiguration;
import org.opensearch.data.client.orhlc.RestClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

//@Profile("prod")
@Configuration
@EnableElasticsearchRepositories(basePackages = {
    "io.mopl.domain.user.repository.search"
})
public class OpenSearchConfig extends AbstractOpenSearchConfiguration {

  @Value("${spring.opensearch.uris}")
  private String opensearchUri;

  @Value("${spring.opensearch.username}")
  private String username;

  @Value("${spring.opensearch.password}")
  private String password;

  @Override
  public RestHighLevelClient opensearchClient() {
    String hostAndPort = opensearchUri.replace("https://", "").replace("http://", "");

    var builder = ClientConfiguration.builder().connectedTo(hostAndPort);

    if (opensearchUri.startsWith("https")) {
      builder.usingSsl();
    }

    if (username != null && !username.isBlank()) {
      builder.withBasicAuth(username, password);
    }

    ClientConfiguration clientConfiguration = builder
        .withConnectTimeout(Duration.ofSeconds(5))
        .withSocketTimeout(Duration.ofSeconds(10))
        .build();

    return RestClients.create(clientConfiguration).rest();
  }
}
