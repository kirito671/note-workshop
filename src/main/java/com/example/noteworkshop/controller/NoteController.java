package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.dto.NoteCreateRequest;
import com.example.noteworkshop.dto.NoteImageOrderRequest;
import com.example.noteworkshop.dto.NoteUpdateRequest;
import com.example.noteworkshop.service.NoteService;
import com.example.noteworkshop.vo.NoteDetailVO;
import com.example.noteworkshop.vo.NoteSummaryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.noteworkshop.vo.NoteImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public ApiResponse<NoteDetailVO> create(@Valid @RequestBody NoteCreateRequest request) {
        return ApiResponse.success(noteService.create(request));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<NoteSummaryVO>> pageMyNotes(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.success(noteService.pageMyNotes(page, size));
    }

    @GetMapping("/public")
    public ApiResponse<PageResponse<NoteSummaryVO>> pagePublicNotes(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(noteService.pagePublicNotes(page, size, keyword));
    }
    @GetMapping("/{id}")
    public ApiResponse<NoteDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(noteService.getDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteDetailVO> update(
            @PathVariable Long id,
            @Valid @RequestBody NoteUpdateRequest request
    ) {
        return ApiResponse.success(noteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return ApiResponse.success(null);
    }
    @PostMapping("/{id}/images")
    public ApiResponse<List<NoteImageVO>> uploadImages(
            @PathVariable Long id,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ){
        return ApiResponse.success(noteService.uploadImages(id, files));
    }
    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<Void> deleteImage(
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        noteService.deleteImage(id, imageId);
        return ApiResponse.success(null);
    }
    @PutMapping("/{id}/images/order")
    public ApiResponse<List<NoteImageVO>> reorderImages(
            @PathVariable Long id,
            @Valid @RequestBody NoteImageOrderRequest request
    ) {
        return ApiResponse.success(noteService.reorderImages(id, request));
    }
}