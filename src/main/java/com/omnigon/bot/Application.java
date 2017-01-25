/*
 * Copyright (c) 2017 Omnigon Communications, LLC. All rights reserved.
 *
 * This software is the confidential and proprietary information of Omnigon Communications, LLC
 * ("Confidential Information"). You shall not disclose such Confidential Information and shall access and use it only
 * in accordance with the terms of the license agreement you entered into with Omnigon Communications, LLC, its
 * subsidiaries, affiliates or authorized licensee. Unless required by applicable law or agreed to in writing, this
 * Confidential Information is provided on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the license agreement for the specific language governing permissions and limitations.
 */
package com.omnigon.bot;

import com.github.messenger4j.MessengerPlatform;
import com.github.messenger4j.send.MessengerSendClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the Spring Boot Application. <br>
 *
 * The Spring Context will be bootstrapped and the application will be configured properly.
 * {@code MessengerSendClient} will be exposed as a singleton Spring Bean, so it is injectable.
 *
 * @author rajesh.kathgave
 */
@SpringBootApplication
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    /**
     * Initializes the {@code MessengerSendClient}.
     *
     * @param pageAccessToken the generated {@code Page Access Token}
     */
    @Bean
    public MessengerSendClient messengerSendClient(@Value("a.pageAccessToken") String pageAccessToken) {
        logger.debug("Initializing MessengerSendClient");
        return MessengerPlatform.newSendClientBuilder(pageAccessToken).build();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
