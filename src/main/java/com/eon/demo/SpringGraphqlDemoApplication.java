package com.eon.demo;

import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.log.LogMessage;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@SpringBootApplication
public class SpringGraphqlDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringGraphqlDemoApplication.class, args);
		
	}

}

@Controller
class CustomerGraphqlController {

	private final CustomerRepository customerRepository;

	CustomerGraphqlController(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@QueryMapping
	Flux<Customer> customers() {
		return this.customerRepository.findAll();
	}

	@QueryMapping
	Flux<Customer> customersByName(@Argument String name) {
		return customerRepository.findByName(name);
	}

	@SchemaMapping(typeName = "Customer")
	Flux<Order> orders(Customer customer) {
		var orders = new ArrayList<Order>();
		for (var orderId =1; orderId<=Math.random()*100; orderId++) {
			orders.add(new Order(orderId, customer.id()));
		}
		return Flux.fromIterable(orders);
	}

	@MutationMapping
	Mono<Customer> addCustomer(@Argument String name) {
		return this.customerRepository.save(new Customer(null, name));
	}

	@SubscriptionMapping
	Flux<CustomerEvent> customerEvents(@Argument Integer customerId){
		return this.customerRepository.findById(customerId)
				.flatMapMany(customer -> {
					var stream = Stream.generate(() -> new CustomerEvent(customer, Math.random() > .5 ? CustomerEventType.DELETED : CustomerEventType.UPDATED));
					return Flux.fromStream(stream);
				})
				.delayElements(Duration.ofSeconds(1))
				.take(20);
	}
}

@Component
class Runner implements CommandLineRunner {

	private Faker faker = new Faker(Locale.GERMAN);
	private final CustomerRepository customerRepository;

	Runner(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@Override
	public void run(String... args) throws Exception {
		for (int i=0; i<1000; i++) {
			customerRepository.save(new Customer(null, faker.name().firstName())).subscribe();
		}
	}
}

interface CustomerRepository extends ReactiveCrudRepository<Customer, Integer> {

	Flux<Customer> findByName(String name);
	
}

record Order(Integer id, Integer customerId) {

}

record Customer(@JsonProperty("id") @Id Integer id, @JsonProperty("name") String name) {
	
}

record CustomerEvent(Customer customer, CustomerEventType type) {}

enum CustomerEventType {UPDATED, DELETED}


@Slf4j
//@Component
class LoggingFilter implements WebFilter {

	public static final ByteArrayOutputStream EMPTY_BYTE_ARRAY_OUTPUT_STREAM = new ByteArrayOutputStream(0);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
			return chain.filter(decorate(exchange));
	}

	private ServerWebExchange decorate(ServerWebExchange exchange) {
		final ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {

			@Override
			public Flux<DataBuffer> getBody() {

				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				return super.getBody().map(dataBuffer -> {
					try {
						Channels.newChannel(baos).write(dataBuffer.asByteBuffer().asReadOnlyBuffer());
					} catch (IOException e) {
						log.error("Unable to log input request due to an error", e);
					}
					return dataBuffer;
				}).doOnComplete(() -> flushLog(baos));
			}

			private void flushLog(ByteArrayOutputStream baos) {

				StringBuffer data = new StringBuffer();

				data.append(" Payload [");

				data.append(baos.toString());

				data.append("]");
				log.info(data.toString());

			}
		};

		return new ServerWebExchangeDecorator(exchange) {

			@Override
			public ServerHttpRequest getRequest() {
				return decorated;
			}

		};
	}
}
