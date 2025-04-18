package org.niit_project.backend.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import io.github.cdimascio.dotenv.Dotenv;
import org.niit_project.backend.dto.ApiResponse;
import org.niit_project.backend.enums.AttachmentType;
import org.niit_project.backend.entities.Chat;
import org.niit_project.backend.enums.MessageType;
import org.niit_project.backend.models.ApiException;
import org.niit_project.backend.service.ChatService;
import org.niit_project.backend.service.DirectMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("scholarly/api/v1/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private DirectMessageService directMessageService;

    @PostMapping(path = "/startChat/{userId}/{recipientId}")
    public ResponseEntity<ApiResponse> startChat(@PathVariable String userId, @PathVariable String recipientId){
        var response = new ApiResponse();
        try{
            var created = chatService.startChat(userId, recipientId);
            response.setMessage("Started Chat");
            response.setData(created);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/getDMs/{userId}")
    public ResponseEntity<ApiResponse> getDMs(@PathVariable String userId){
        var response = new ApiResponse();

        try {
            var gottenDMs = directMessageService.getDirectMessages(userId);
            response.setMessage("Got DMs Successfully");
            response.setData(gottenDMs);
            return new ResponseEntity<>(response, HttpStatus.OK);

        }
        catch (Exception e){
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(path = "/searchUser")
    public ResponseEntity<ApiResponse> searchUser(@RequestBody Map<String, String> body){
        System.out.println(body);
        var response = new ApiResponse();

        try{
            var results = directMessageService.searchUser(body.get("search").trim());
            response.setMessage("Got Users");
            response.setData(results);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }


    @PostMapping(path = "/sendChat/{dmId}/{senderId}")
    public ResponseEntity<ApiResponse> sendChat(@PathVariable String dmId, @PathVariable String senderId, @RequestBody Chat chat){
        var response = new ApiResponse();


        /// This endpoint is run by the front-end for only message-based (text or attachment) chats
        /// Not Joined or Removed chats
        chat.setMessageType(MessageType.chat);

        try {
            var sentChat = chatService.createChat(chat, dmId, senderId);
            response.setMessage("Sent Chat Successfully");
            response.setData(sentChat);

            return new ResponseEntity<>(response, HttpStatus.OK);

        }
        catch (Exception e){
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path = "/getChats/{dmId}")
    public ResponseEntity<ApiResponse> getChat(@PathVariable String dmId){
        var response = new ApiResponse();

        try {
            var gottenChats = chatService.getChats(dmId);
            response.setMessage("Gotten Chats Successfully");
            response.setData(gottenChats);
            return new ResponseEntity<>(response, HttpStatus.OK);

        }
        catch (Exception e){
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value = "/sendAttachment/{dmId}/{senderId}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse> sendAttachmentChat(@PathVariable String dmId, @PathVariable String senderId, @RequestPart("attachment") MultipartFile attachment, @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail ,@RequestPart(value = "attachmentType", required = false) String type, @RequestPart(value = "message", required = false) String message){
        var response = new ApiResponse();


        try{
            var attachmentType = type == null? AttachmentType.image: AttachmentType.valueOf(type);
            var resource_type = List.of(AttachmentType.image, AttachmentType.video).contains(attachmentType)?attachmentType.name().toLowerCase() :"raw";
            var fileName = attachment.getOriginalFilename();
            var chat = new Chat();
            var dotenv = Dotenv.load();
            var cloudinary = new Cloudinary(dotenv.get("CLOUDINARY_URL"));

            if(thumbnail != null && !thumbnail.isEmpty() && attachmentType == AttachmentType.video || attachmentType == AttachmentType.audio){
                var params = ObjectUtils.asMap(
                        "use_filename", false,
                        "resource_type", "image",
                        "unique_filename", true,
                        "overwrite", false
                );
                var thumbnailUrl = cloudinary.uploader().upload(thumbnail.getBytes(), params);
                var thumbnail_url = thumbnailUrl.get("secure_url").toString();
                chat.setThumbnail(thumbnail_url);
            }

            var params = ObjectUtils.asMap(
                    "use_filename", true,
                    "resource_type", resource_type,
                    "unique_filename", true,
                    "overwrite", false
            );

            var tempFile = File.createTempFile("upload-",fileName);
            try(var inputStream = attachment.getInputStream();
                var outputStream = new FileOutputStream(tempFile);
            ){
                inputStream.transferTo(outputStream);
            }


            var result = cloudinary.uploader().upload(tempFile, params);
            // We have to delete the tempFile Immediately It's done uploading
            tempFile.delete();
            var secure_url = result.get("secure_url");





            chat.setAttachment(secure_url.toString());
            chat.setDmId(dmId);
            chat.setSenderId(senderId);
            chat.setMessage(message);
            chat.setMessageType(MessageType.chat);
            chat.setFileName(fileName);
            chat.setAttachmentType(attachmentType);

            var createdChat = chatService.createChat(chat, dmId, senderId);
            response.setMessage("Uploaded Attachment Successfully");
            response.setData(createdChat);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @PatchMapping(path = "/markChatAsRead/{dmId}/{userId}/{chatId}")
    public ResponseEntity<ApiResponse> markChatAsRead(@PathVariable String chatId, @PathVariable String dmId, @PathVariable("userId") String memberId){
        var response = new ApiResponse();

        try{
            var markedChat = chatService.markChatAsRead(memberId, dmId, chatId);
            response.setMessage("Marked Chat As Read");
            response.setData(markedChat);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

    }

    @DeleteMapping(path = "/closeDm/{dmId}")
    public ResponseEntity<ApiResponse> closeDM(@PathVariable String dmId){
        var response = new ApiResponse();
        try {
            var dm = directMessageService.clearDm(dmId);
            response.setMessage("Closed DM");
            response.setData(dm);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ApiException e) {
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, e.getStatusCode());
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

//    @PatchMapping(path = "/markAllChatsAsRead/{channelId}/{userId}")
//    public ResponseEntity<ApiResponse> markAllChatsAsRead(@PathVariable String channelId, @PathVariable("userId") String memberId){
//        var response = new ApiResponse();
//
//        try{
//            var markedChat = chatService.markChatAsRead(memberId, channelId, chatId);
//            response.setMessage("Marked All Chats As Read");
//            response.setData(markedChat);
//            return new ResponseEntity<>(response, HttpStatus.OK);
//        } catch (Exception e) {
//            response.setMessage(e.getMessage());
//            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
//        }
//    }
}
