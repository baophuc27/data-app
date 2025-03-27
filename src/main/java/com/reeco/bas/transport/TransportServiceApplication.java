package com.reeco.bas.transport;

import com.reeco.bas.transport.application.CacheStorageService;
import com.reeco.bas.transport.receiver.DeviceReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class TransportServiceApplication {


	private static String myVariable;

	public static void main(String[] args) {
//		SpringApplication.run(TransportServiceApplication.class, args);
		ConfigurableApplicationContext context = SpringApplication.run(TransportServiceApplication.class, args);
		Environment env = context.getEnvironment();
		myVariable = env.getProperty("my.variable");
		System.out.println("Env var: " + myVariable );
	}
}
