package com.graphhopper.bug;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class App extends Application<Configuration> {

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        bootstrap.setObjectMapper(io.dropwizard.jackson.Jackson.newMinimalObjectMapper());
    }

    @Override
    public void run(Configuration config, Environment environment) {
        environment.jersey().register(InterfereResource.class);
        environment.jersey().register(WorkResource.class);
        environment.jersey().register(environment.healthChecks());
    }

}
