package com.unblu.middleware.test;

import com.unblu.middleware.bots.annotation.UnbluBots;
import com.unblu.middleware.bots.service.DialogBotService;
import com.unblu.middleware.webhooks.annotation.UnbluWebhooks;
import com.unblu.middleware.webhooks.service.WebhookHandlerService;
import com.unblu.webapi.jersey.v4.api.BotsApi;
import com.unblu.webapi.jersey.v4.invoker.ApiException;
import com.unblu.webapi.model.v4.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.unblu.middleware.common.registry.RequestOrderSpec.canIgnoreOrder;
import static com.unblu.middleware.webhooks.entity.EventName.eventName;

@Service
@Slf4j
@RequiredArgsConstructor
@Import({
        UnbluBots.class,
        UnbluWebhooks.class
})
public class TestMiddlewareService implements ApplicationRunner {

    private final WebhookHandlerService webhookHandlerService;
    private final DialogBotService dialogBotService;
    private final BotsApi botsApi;

    @Override
    public void run(ApplicationArguments args) {
        // accept every onboarding offer
        dialogBotService.acceptOnboardingOfferIf(_o -> Mono.just(true));

        // greet the user when a dialog is opened
        dialogBotService.onDialogOpen(r ->
                Mono.fromRunnable(() -> sendMessage(r.getDialogToken(), "Hello, I am a bot!")));

        // echo every message back to the user
        dialogBotService.onDialogMessage(r ->
                Mono.fromRunnable(() -> echoIfSentByVisitor(r)));

        // log every message sent anywhere using a webhook handler
        webhookHandlerService.onWebhook(eventName("conversation.new_message"), ConversationNewMessageEvent.class,
                e -> Mono.fromRunnable(() -> log.info("Message received: {}", e.getConversationMessage().getFallbackText())),
                canIgnoreOrder());
    }

    private void echoIfSentByVisitor(BotDialogMessageRequest r) {
        if (r.getConversationMessage().getSenderPerson().getPersonType() == EPersonType.VISITOR) {
            sendMessage(r.getDialogToken(), "You wrote: " + r.getConversationMessage().getFallbackText());
        }
    }

    private void sendMessage(String dialogToken, String text) {
        try {
            botsApi.botsSendDialogMessage(new BotDialogPostMessage()
                    .dialogToken(dialogToken)
                    .messageData(new TextPostMessageData().text(text)));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}
