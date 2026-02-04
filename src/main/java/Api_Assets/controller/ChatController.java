package Api_Assets.controller;

import Api_Assets.dto.ChatRequest;
import Api_Assets.dto.ChatResponse;
import Api_Assets.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")  // For Postman testing
public class ChatController {

    @Autowired
    private ChatService chatService;

    //  GET version (for Postman URL params)
    @GetMapping
    public ResponseEntity<ChatResponse> chatGet(@RequestParam String message) {
        String reply = chatService.processMessage(message);
        return ResponseEntity.ok(new ChatResponse(reply));
    }

    //  POST version (for JSON body - better for production)
    @PostMapping
    public ResponseEntity<ChatResponse> chatPost(@RequestBody ChatRequest request) {
        String reply = chatService.processMessage(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(reply));
    }
}
