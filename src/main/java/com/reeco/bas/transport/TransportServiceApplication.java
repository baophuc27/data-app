package com.reeco.bas.transport;

import com.reeco.bas.transport.application.VesselStateMachine;
import com.reeco.bas.transport.model.VesselState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class TransportServiceApplication {

	private static String myVariable;

	@Autowired
	private VesselStateMachine vesselStateMachine;

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(TransportServiceApplication.class, args);
		Environment env = context.getEnvironment();
		myVariable = env.getProperty("my.variable");
		System.out.println("Env var: " + myVariable);
	}

	@Bean
	public CommandLineRunner initializeVesselState() {
		return args -> {
			// Initialize vessel state to AVAILABLE by default
			vesselStateMachine.initializeWithState(VesselState.AVAILABLE);
		};
	}
}