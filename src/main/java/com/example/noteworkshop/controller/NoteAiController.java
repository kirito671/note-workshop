package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.dto.NoteAiSaveRequest;
import com.example.noteworkshop.service.NoteAiService;
import com.example.noteworkshop.vo.AiAnalysisVO;
import com.example.noteworkshop.vo.NoteAiResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes/{noteId}")
public class NoteAiController {
    private final NoteAiService noteAiService;
    public NoteAiController(NoteAiService noteAiService) { this.noteAiService = noteAiService; }

    @PostMapping("/ai-analysis")
    public ApiResponse<AiAnalysisVO> analyze(@PathVariable Long noteId) {
        return ApiResponse.success(noteAiService.analyze(noteId));
    }

    @PostMapping("/ai-result")
    public ApiResponse<NoteAiResultVO> save(@PathVariable Long noteId,
            @Valid @RequestBody NoteAiSaveRequest request) {
        return ApiResponse.success(noteAiService.saveResult(noteId, request));
    }

    @DeleteMapping("/ai-result")
    public ApiResponse<Void> delete(@PathVariable Long noteId) {
        noteAiService.deleteResult(noteId);
        return ApiResponse.success(null);
    }
}