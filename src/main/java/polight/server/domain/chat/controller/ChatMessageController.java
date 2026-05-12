package polight.server.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.chat.service.ChatMessageService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-messages")
public class ChatMessageController {

  private final ChatMessageService chatMessageService;
}
