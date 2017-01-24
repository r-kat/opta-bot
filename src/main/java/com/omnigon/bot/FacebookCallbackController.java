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
import com.github.messenger4j.exceptions.MessengerApiException;
import com.github.messenger4j.exceptions.MessengerIOException;
import com.github.messenger4j.exceptions.MessengerVerificationException;
import com.github.messenger4j.receive.MessengerReceiveClient;
import com.github.messenger4j.receive.events.AccountLinkingEvent.AccountLinkingStatus;
import com.github.messenger4j.receive.events.AttachmentMessageEvent.Attachment;
import com.github.messenger4j.receive.events.AttachmentMessageEvent.AttachmentType;
import com.github.messenger4j.receive.events.AttachmentMessageEvent.Payload;
import com.github.messenger4j.receive.handlers.*;
import com.github.messenger4j.send.*;
import com.github.messenger4j.send.buttons.Button;
import com.github.messenger4j.send.templates.ButtonTemplate;
import com.github.messenger4j.send.templates.GenericTemplate;
import com.github.messenger4j.send.templates.ReceiptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

import static com.github.messenger4j.MessengerPlatform.*;
import static com.omnigon.bot.support.App.Samples.*;
import static com.omnigon.bot.support.App.Var.APP_SECRET;
import static com.omnigon.bot.support.App.Var.VERIFY_TOKEN;

/**
 * This is the main class for inbound and outbound communication with the Facebook Messenger Platform. <br>
 * The callback handler is responsible for the webhook verification and processing of the inbound messages and events.
 *
 * @author rajesh.kathgave
 */
@RestController
@RequestMapping("/callback")
public class FacebookCallbackController {
    private static final Logger logger = LoggerFactory.getLogger(FacebookCallbackController.class);

    private MessengerReceiveClient receiveClient;
    private MessengerSendClient sendClient;

    /**
     * Constructs the {@link FacebookCallbackController} and initializes the {@link MessengerReceiveClient}.
     *
     * @param appSecret   {@code Application Secret}
     * @param verifyToken {@code Verification Token} that has been provided by you during the setup of the {@code
     *                    Webhook}
     * @param sendClient  initialized {@code MessengerSendClient}
     */
    @Autowired
    public FacebookCallbackController(@Value(APP_SECRET) String appSecret,
                                      @Value(VERIFY_TOKEN) String verifyToken,
                                      MessengerSendClient sendClient) {
        logger.debug("Initializing facebook controller");
        this.receiveClient = MessengerPlatform.newReceiveClientBuilder(appSecret, verifyToken)
                .onTextMessageEvent(textMessageEventHandler())
                .onAttachmentMessageEvent(attachmentMessageEventHandler())
                .onQuickReplyMessageEvent(quickReplyMessageEventHandler())
                .onPostbackEvent(postbackEventHandler())
                .onAccountLinkingEvent(accountLinkingEventHandler())
                .onOptInEvent(optInEventHandler())
                .onEchoMessageEvent(echoMessageEventHandler())
                .onMessageDeliveredEvent(messageDeliveredEventHandler())
                .onMessageReadEvent(messageReadEventHandler())
                .fallbackEventHandler(fallbackEventHandler())
                .build();
        this.sendClient = sendClient;
    }

    /**
     * Facebook <a href="https://developers.facebook.com/docs/graph-api/webhooks">webhook</a> verification endpoint.
     *
     * The passed verification token (as query parameter) must match the configured verification token. If true,
     * the passed challenge string must be returned by this endpoint.
     */
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> verifyWebhook(@RequestParam(MODE_REQUEST_PARAM_NAME) final String mode,
                                                @RequestParam(VERIFY_TOKEN_REQUEST_PARAM_NAME) final String verifyToken,
                                                @RequestParam(CHALLENGE_REQUEST_PARAM_NAME) final String challenge) {

        logger.debug("Received webhook verification request: Mode={} | VerifyToken={} | Challenge={}", mode,
                verifyToken, challenge);
        try {
            return ResponseEntity.ok(this.receiveClient.verifyWebhook(mode, verifyToken, challenge));
        } catch (MessengerVerificationException e) {
            logger.warn("Webhook verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * Callback endpoint responsible for processing the inbound messages and events.
     */
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<Void> handleCallback(@RequestBody final String payload,
                                               @RequestHeader(SIGNATURE_HEADER_NAME) final String signature) {

        logger.debug("Received messenger platform callback - payload={} | signature={}", payload, signature);
        try {
            this.receiveClient.processCallbackPayload(payload, signature);
            logger.debug("Processed callback payload successfully");
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (MessengerVerificationException e) {
            logger.warn("Processing of callback payload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private TextMessageEventHandler textMessageEventHandler() {
        return event -> {
            logger.debug("Received message event: {}", event);

            final String messageId = event.getMid();
            final String messageText = event.getText();
            final String senderId = event.getSender().getId();
            final Date timestamp = event.getTimestamp();

            logger.info("Received message '{}' with text {} from user {} at {}", messageId, messageText, senderId, timestamp);

            try {
                switch (messageText.toLowerCase()) {
                    case "image":
                        sendImageMessage(senderId);
                        break;

                    case "gif":
                        sendGifMessage(senderId);
                        break;

                    case "video":
                        sendVideoMessage(senderId);
                        break;

                    case "file":
                        sendFileMessage(senderId);
                        break;

                    case "button":
                        sendButtonMessage(senderId);
                        break;

                    case "generic":
                        sendGenericMessage(senderId);
                        break;

                    case "quick reply":
                        sendQuickReply(senderId);
                        break;

                    case "read receipt":
                        sendReadReceipt(senderId);
                        break;

                    case "typing on":
                        sendTypingOn(senderId);
                        break;

                    case "typing off":
                        sendTypingOff(senderId);
                        break;

                    default:
                        sendTextMessage(senderId, messageText);
                }
            } catch (MessengerApiException | MessengerIOException e) {
                handleSendException(e);
            }
        };
    }

    private void sendImageMessage(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendImageAttachment(recipientId, IMAGE);
    }

    private void sendGifMessage(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendImageAttachment(recipientId, GIF);
    }

    private void sendVideoMessage(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendVideoAttachment(recipientId, VIDEO);
    }

    private void sendFileMessage(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendFileAttachment(recipientId, TEXT_FILE);
    }

    private void sendButtonMessage(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<Button> buttons = Button.newListBuilder()
                .addUrlButton("Open Web URL", WEBSITE_URL).toList()
                .addPostbackButton("Trigger Postback", "DEVELOPER_DEFINED_PAYLOAD").toList()
                .addCallButton("Call Phone Number", PHONE_NUMBER).toList()
                .build();

        final ButtonTemplate buttonTemplate = ButtonTemplate.newBuilder("Tap a button", buttons).build();
        this.sendClient.sendTemplate(recipientId, buttonTemplate);
    }

    private void sendGenericMessage(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<Button> riftButtons = Button.newListBuilder()
                .addUrlButton("Open Web URL", WEBSITE_URL).toList()
                .addPostbackButton("Call Postback", "Payload for first bubble").toList()
                .build();

        final List<Button> touchButtons = Button.newListBuilder()
                .addUrlButton("Open Web URL", WEBSITE_URL).toList()
                .addPostbackButton("Call Postback", "Payload for second bubble").toList()
                .build();


        final GenericTemplate genericTemplate = GenericTemplate.newBuilder()
                .addElements()
                .addElement("AS Roma")
                .subtitle("Italina football team")
                .itemUrl(WEBSITE_URL)
                .imageUrl(WEBSITE_LOGO)
                .buttons(riftButtons)
                .toList()
                .addElement("Schedule")
                .subtitle("AS Roma Schedule")
                .itemUrl(SCHEDULE)
                .imageUrl(WEBSITE_LOGO)
                .buttons(touchButtons)
                .toList()
                .done()
                .build();

        this.sendClient.sendTemplate(recipientId, genericTemplate);
    }

    private void sendQuickReply(String recipientId) throws MessengerApiException, MessengerIOException {
        final List<QuickReply> quickReplies = QuickReply.newListBuilder()
                .addTextQuickReply("Action", "DEVELOPER_DEFINED_PAYLOAD_FOR_PICKING_ACTION").toList()
                .addTextQuickReply("Comedy", "DEVELOPER_DEFINED_PAYLOAD_FOR_PICKING_COMEDY").toList()
                .addTextQuickReply("Drama", "DEVELOPER_DEFINED_PAYLOAD_FOR_PICKING_DRAMA").toList()
                .addLocationQuickReply().toList()
                .build();

        this.sendClient.sendTextMessage(recipientId, "What's your favorite movie genre?", quickReplies);
    }

    private void sendReadReceipt(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendSenderAction(recipientId, SenderAction.MARK_SEEN);
    }

    private void sendTypingOn(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendSenderAction(recipientId, SenderAction.TYPING_ON);
    }

    private void sendTypingOff(String recipientId) throws MessengerApiException, MessengerIOException {
        this.sendClient.sendSenderAction(recipientId, SenderAction.TYPING_OFF);
    }

    private void sendAccountLinking(String recipientId) {
        // supported by messenger4j since 0.7.0
        // sample implementation coming soon
    }

    private AttachmentMessageEventHandler attachmentMessageEventHandler() {
        return event -> {
            logger.debug("Received AttachmentMessageEvent: {}", event);

            final String messageId = event.getMid();
            final List<Attachment> attachments = event.getAttachments();
            final String senderId = event.getSender().getId();
            final Date timestamp = event.getTimestamp();

            logger.info("Received message '{}' with attachments from user '{}' at '{}':",
                    messageId, senderId, timestamp);

            attachments.forEach(attachment -> {
                final AttachmentType attachmentType = attachment.getType();
                final Payload payload = attachment.getPayload();

                String payloadAsString = null;
                if (payload.isBinaryPayload()) {
                    payloadAsString = payload.asBinaryPayload().getUrl();
                }
                if (payload.isLocationPayload()) {
                    payloadAsString = payload.asLocationPayload().getCoordinates().toString();
                }

                logger.info("Attachment of type '{}' with payload '{}'", attachmentType, payloadAsString);
            });

            sendTextMessage(senderId, "Message with attachment received");
        };
    }

    private QuickReplyMessageEventHandler quickReplyMessageEventHandler() {
        return event -> {
            logger.debug("Received QuickReplyMessageEvent: {}", event);

            final String senderId = event.getSender().getId();
            final String messageId = event.getMid();
            final String quickReplyPayload = event.getQuickReply().getPayload();

            logger.info("Received quick reply for message '{}' with payload '{}'", messageId, quickReplyPayload);

            sendTextMessage(senderId, "Quick reply tapped");
        };
    }

    private PostbackEventHandler postbackEventHandler() {
        return event -> {
            logger.debug("Received PostbackEvent: {}", event);

            final String senderId = event.getSender().getId();
            final String recipientId = event.getRecipient().getId();
            final String payload = event.getPayload();
            final Date timestamp = event.getTimestamp();

            logger.info("Received postback for user '{}' and page '{}' with payload '{}' at '{}'",
                    senderId, recipientId, payload, timestamp);

            sendTextMessage(senderId, "Postback called");
        };
    }

    private AccountLinkingEventHandler accountLinkingEventHandler() {
        return event -> {
            logger.debug("Received AccountLinkingEvent: {}", event);

            final String senderId = event.getSender().getId();
            final AccountLinkingStatus accountLinkingStatus = event.getStatus();
            final String authorizationCode = event.getAuthorizationCode();

            logger.info("Received account linking event for user '{}' with status '{}' and auth code '{}'",
                    senderId, accountLinkingStatus, authorizationCode);
        };
    }

    private OptInEventHandler optInEventHandler() {
        return event -> {
            logger.debug("Received OptInEvent: {}", event);

            final String senderId = event.getSender().getId();
            final String recipientId = event.getRecipient().getId();
            final String passThroughParam = event.getRef();
            final Date timestamp = event.getTimestamp();

            logger.info("Received authentication for user '{}' and page '{}' with pass through param '{}' at '{}'",
                    senderId, recipientId, passThroughParam, timestamp);

            sendTextMessage(senderId, "Authentication successful");
        };
    }

    private EchoMessageEventHandler echoMessageEventHandler() {
        return event -> {
            logger.debug("Received EchoMessageEvent: {}", event);

            final String messageId = event.getMid();
            final String recipientId = event.getRecipient().getId();
            final String senderId = event.getSender().getId();
            final Date timestamp = event.getTimestamp();

            logger.info("Received echo for message '{}' that has been sent to recipient '{}' by sender '{}' at '{}'",
                    messageId, recipientId, senderId, timestamp);
        };
    }

    private MessageDeliveredEventHandler messageDeliveredEventHandler() {
        return event -> {
            logger.debug("Received MessageDeliveredEvent: {}", event);

            final List<String> messageIds = event.getMids();
            final Date watermark = event.getWatermark();
            final String senderId = event.getSender().getId();

            if (messageIds != null) {
                messageIds.forEach(messageId -> {
                    logger.info("Received delivery confirmation for message '{}'", messageId);
                });
            }

            logger.info("All messages before '{}' were delivered to user '{}'", watermark, senderId);
        };
    }

    private MessageReadEventHandler messageReadEventHandler() {
        return event -> {
            logger.debug("Received MessageReadEvent: {}", event);

            final Date watermark = event.getWatermark();
            final String senderId = event.getSender().getId();

            logger.info("All messages before '{}' were read by user '{}'", watermark, senderId);
        };
    }

    /**
     * This handler is called when either the message is unsupported or when the event handler for the actual event type
     * is not registered. In this showcase all event handlers are registered. Hence only in case of an
     * unsupported message the fallback event handler is called.
     */
    private FallbackEventHandler fallbackEventHandler() {
        return event -> {
            logger.debug("Received FallbackEvent: {}", event);

            final String senderId = event.getSender().getId();
            logger.info("Received unsupported message from user '{}'", senderId);
        };
    }

    private void sendTextMessage(String recipientId, String text) {
        try {
            final Recipient recipient = Recipient.newBuilder().recipientId(recipientId).build();
            final NotificationType notificationType = NotificationType.REGULAR;
            final String metadata = "DEVELOPER_DEFINED_METADATA";

            this.sendClient.sendTextMessage(recipient, notificationType, text, metadata);
        } catch (MessengerApiException | MessengerIOException e) {
            handleSendException(e);
        }
    }

    private void handleSendException(Exception e) {
        logger.error("Message could not be sent. An unexpected error occurred.", e);
    }
}
