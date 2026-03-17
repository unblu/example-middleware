package com.unblu.middleware.test;

import com.unblu.middleware.bots.service.DialogBot;
import com.unblu.middleware.common.entity.ContextSpec;
import com.unblu.middleware.webhooks.entity.WebhookHandlerOptions;
import com.unblu.middleware.webhooks.service.WebhookHandler;
import com.unblu.webapi.jersey.v4.api.BotsApi;
import com.unblu.webapi.jersey.v4.invoker.ApiException;
import com.unblu.webapi.model.v4.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import static com.unblu.middleware.webhooks.entity.EventName.eventName;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestMiddlewareService implements ApplicationRunner {

    private final WebhookHandler webhookHandler;
    private final DialogBot dialogBot;
    private final BotsApi botsApi;

    @Override
    public void run(ApplicationArguments args) {
        // accept every onboarding offer
        dialogBot.acceptOnboardingOfferIf(_o -> true);

        // greet the user when a dialog is opened
        dialogBot.onDialogOpen(r -> sendMessage(r.getDialogToken(), "Hello, I am a bot!"));

        // echo every message back to the user
        dialogBot.onDialogMessage(this::echoIfSentByVisitor);

        // log every message sent anywhere using a webhook handler
        webhookHandler.on(eventName("conversation.new_message"), ConversationNewMessageEvent.class,
                e -> log.info("Message received: {}", e.getConversationMessage().getFallbackText()),
                WebhookHandlerOptions.contextSpec(
                        ContextSpec.of(
                                "conversationId", it -> it.getConversationMessage().getConversationId()
                        )));
    }

    private void echoIfSentByVisitor(BotDialogMessageRequest r) {
        if (r.getConversationMessage().getSenderPerson().getPersonType() == EPersonType.VISITOR) {
            sendMessage(r.getDialogToken(), "You wrote: " + r.getConversationMessage().getFallbackText());
        }
    }

    private void sendMessage(String dialogToken, String text) {
        try {
            log.info("Sending message: {}", text);
            botsApi.botsSendDialogMessage(new BotDialogPostMessage()
                    .dialogToken(dialogToken)
                    .messageData(new TextPostMessageData().text(text)));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}
