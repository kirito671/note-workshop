package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.dto.NoteCreateRequest;
import com.example.noteworkshop.dto.NoteImageOrderRequest;
import com.example.noteworkshop.dto.NoteUpdateRequest;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.NoteImage;
import com.example.noteworkshop.entity.NoteLike;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.NoteImageMapper;
import com.example.noteworkshop.mapper.NoteLikeMapper;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.security.CurrentUser;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.NoteDetailVO;
import com.example.noteworkshop.vo.NoteImageVO;
import com.example.noteworkshop.vo.NoteSummaryVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NoteService {
    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final NoteLikeMapper noteLikeMapper;
    private final FileStorageService fileStorageService;
    private final int maxNoteImageCount;

    public NoteService(
            NoteMapper noteMapper,
            NoteImageMapper noteImageMapper,
            NoteLikeMapper noteLikeMapper,
            FileStorageService fileStorageService,
            @Value("${app.upload.max-note-image-count}") int maxNoteImageCount
    ) {
        if (maxNoteImageCount <= 0) {
            throw new IllegalArgumentException("Max note image count must be greater than 0");
        }
        this.noteMapper = noteMapper;
        this.noteImageMapper = noteImageMapper;
        this.noteLikeMapper = noteLikeMapper;
        this.fileStorageService = fileStorageService;
        this.maxNoteImageCount = maxNoteImageCount;
    }

    @Transactional
    public NoteDetailVO create(NoteCreateRequest request) {
        CurrentUser currentUser = UserContext.get();

        Note note = new Note();
        note.setUserId(currentUser.id());
        note.setTitle(request.title().trim());
        note.setBookName(request.bookName().trim());
        note.setContent(request.content().trim());
        note.setVisibility(request.visibility());
        note.setStatus(1);

        noteMapper.insert(note);
        Note savedNote = requireNote(note.getId());
        return toDetailVO(savedNote);
    }

    public NoteDetailVO getDetail(Long id) {
        Note note = requireNote(id);
        CurrentUser currentUser = UserContext.get();

        // 隐藏笔记：只有作者或管理员可查看
        if (!Integer.valueOf(1).equals(note.getStatus())
                && !canManageNote(note, currentUser)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        }

        // 私有笔记：只有作者或管理员可查看
        if (Integer.valueOf(0).equals(note.getVisibility())
                && !canManageNote(note, currentUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该私有笔记");
        }

        return toDetailVO(note);
    }

    @Transactional
    public NoteDetailVO update(Long id, NoteUpdateRequest request) {
        Note note = requireNote(id);
        requireManagePermission(note, UserContext.get());

        noteMapper.update(
                null,
                Wrappers.<Note>lambdaUpdate()
                        .eq(Note::getId, id)
                        .set(Note::getTitle, request.title().trim())
                        .set(Note::getBookName, request.bookName().trim())
                        .set(Note::getContent, request.content().trim())
                        .set(Note::getVisibility, request.visibility())
        );

        return toDetailVO(requireNote(id));
    }

    @Transactional
    public void delete(Long id) {
        Note note = requireNote(id);
        requireManagePermission(note, UserContext.get());

        List<String> imageUrls = noteImageMapper.selectList(
                Wrappers.<NoteImage>lambdaQuery()
                        .eq(NoteImage::getNoteId, id)
        ).stream().map(NoteImage::getImageUrl).toList();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        imageUrls.forEach(fileStorageService::deleteNoteImageByUrl);
                    }
                }
        );

        noteImageMapper.delete(
                Wrappers.<NoteImage>lambdaQuery().eq(NoteImage::getNoteId, id)
        );
        noteLikeMapper.delete(
                Wrappers.<NoteLike>lambdaQuery()
                        .eq(NoteLike::getNoteId, id)
        );
        noteMapper.deleteById(id);
    }
    @Transactional
    public List<NoteImageVO> uploadImages(Long noteId, List<MultipartFile> files) {
        Note note = requireNote(noteId);
        requireManagePermission(note, UserContext.get());

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传图片不能为空");
        }
        if (files.size() > maxNoteImageCount) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "单次最多上传 " + maxNoteImageCount + " 张图片"
            );
        }

        List<NoteImage> existingImages = noteImageMapper.selectList(
                Wrappers.<NoteImage>lambdaQuery()
                        .eq(NoteImage::getNoteId, noteId)
        );
        if (existingImages.size() + files.size() > maxNoteImageCount) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "一条笔记最多保存 " + maxNoteImageCount + " 张图片"
            );
        }

        int nextSortOrder = existingImages.stream()
                .mapToInt(NoteImage::getSortOrder)
                .max()
                .orElse(-1) + 1;
        List<FileStorageService.StoredNoteImage> storedImages = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                storedImages.add(fileStorageService.storeNoteImage(noteId, file));
            }

            List<NoteImageVO> uploadedImages = new ArrayList<>();
            for (int index = 0; index < storedImages.size(); index++) {
                FileStorageService.StoredNoteImage storedImage = storedImages.get(index);
                NoteImage noteImage = new NoteImage();
                noteImage.setNoteId(noteId);
                noteImage.setImageUrl(storedImage.imageUrl());
                noteImage.setSortOrder(nextSortOrder + index);
                noteImageMapper.insert(noteImage);
                uploadedImages.add(new NoteImageVO(
                        noteImage.getId(),
                        noteImage.getImageUrl(),
                        noteImage.getSortOrder()
                ));
            }
            return uploadedImages;
        } catch (RuntimeException exception) {
            fileStorageService.deleteStoredNoteImages(storedImages);
            throw exception;
        }
    }

    @Transactional
    public void deleteImage(Long noteId, Long imageId) {
        Note note = requireNote(noteId);
        requireManagePermission(note, UserContext.get());

        NoteImage noteImage = noteImageMapper.selectById(imageId);
        if (noteImage == null || !noteId.equals(noteImage.getNoteId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }

        int deletedRows = noteImageMapper.deleteById(imageId);
        if (deletedRows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }

        fileStorageService.deleteNoteImageByUrl(noteImage.getImageUrl());
    }

    @Transactional
    public List<NoteImageVO> reorderImages(Long noteId, NoteImageOrderRequest request) {
        Note note = requireNote(noteId);
        requireManagePermission(note, UserContext.get());

        if (request == null || request.imageIds() == null || request.imageIds().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片排序列表不能为空");
        }

        List<Long> requestedIds = request.imageIds();
        Set<Long> uniqueRequestedIds = new HashSet<>(requestedIds);
        List<NoteImage> images = noteImageMapper.selectList(
                Wrappers.<NoteImage>lambdaQuery()
                        .eq(NoteImage::getNoteId, noteId)
        );

        Set<Long> existingIds = new HashSet<>();
        Map<Long, NoteImage> imageById = new HashMap<>();
        for (NoteImage image : images) {
            existingIds.add(image.getId());
            imageById.put(image.getId(), image);
        }

        if (uniqueRequestedIds.size() != requestedIds.size()
                || !uniqueRequestedIds.equals(existingIds)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片排序列表与笔记图片不一致");
        }

        List<NoteImageVO> orderedImages = new ArrayList<>(requestedIds.size());
        for (int sortOrder = 0; sortOrder < requestedIds.size(); sortOrder++) {
            Long imageId = requestedIds.get(sortOrder);
            NoteImage image = imageById.get(imageId);
            noteImageMapper.update(
                    null,
                    Wrappers.<NoteImage>lambdaUpdate()
                            .eq(NoteImage::getId, imageId)
                            .eq(NoteImage::getNoteId, noteId)
                            .set(NoteImage::getSortOrder, sortOrder)
            );
            image.setSortOrder(sortOrder);
            orderedImages.add(new NoteImageVO(
                    image.getId(),
                    image.getImageUrl(),
                    image.getSortOrder()
            ));
        }
        return orderedImages;
    }

    public PageResponse<NoteSummaryVO> pageLikedNotes(long page, long size) {
        validatePage(page, size);
        CurrentUser currentUser = UserContext.get();
        Page<Note> result = noteMapper.selectLikedNotes(
                Page.of(page, size),
                currentUser.id()
        );
        return toPageResponse(result);
    }
    public PageResponse<NoteSummaryVO> pageRanking(long page, long size) {
        validatePage(page, size);
        Page<Note> result = noteMapper.selectRankingNotes(Page.of(page, size));
        return toPageResponse(result);
    }

    public PageResponse<NoteSummaryVO> pageMyNotes(long page, long size) {
        validatePage(page, size);
        CurrentUser currentUser = UserContext.get();

        Page<Note> result = noteMapper.selectPage(
                Page.of(page, size),
                Wrappers.<Note>lambdaQuery()
                        .eq(Note::getUserId, currentUser.id())
                        .orderByDesc(Note::getCreatedAt)
                        .orderByDesc(Note::getId)
        );

        return toPageResponse(result);
    }

    public PageResponse<NoteSummaryVO> pagePublicNotes(long page, long size, String keyword) {
        validatePage(page, size);

        var wrapper = Wrappers.<Note>lambdaQuery()
                .eq(Note::getVisibility, 1)
                .eq(Note::getStatus, 1);

        if (StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            wrapper.and(condition -> condition
                    .like(Note::getTitle, trimmedKeyword)
                    .or()
                    .like(Note::getBookName, trimmedKeyword));
        }

        wrapper.orderByDesc(Note::getCreatedAt)
                .orderByDesc(Note::getId);

        Page<Note> result = noteMapper.selectPage(Page.of(page, size), wrapper);
        return toPageResponse(result);
    }

    private void validatePage(long page, long size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }

    private PageResponse<NoteSummaryVO> toPageResponse(Page<Note> pageResult) {
        List<Note> notes = pageResult.getRecords();
        if (notes.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    pageResult.getTotal(),
                    pageResult.getCurrent(),
                    pageResult.getSize(),
                    pageResult.getPages()
            );
        }

        List<Long> noteIds = notes.stream()
                .map(Note::getId)
                .toList();
        List<NoteLike> noteLikes = noteLikeMapper.selectList(
                Wrappers.<NoteLike>lambdaQuery()
                        .in(NoteLike::getNoteId, noteIds)
        );
        CurrentUser currentUser = UserContext.get();
        Map<Long, Long> likeCounts = new HashMap<>();
        Set<Long> likedNoteIds = new HashSet<>();
        for (NoteLike noteLike : noteLikes) {
            likeCounts.merge(noteLike.getNoteId(), 1L, Long::sum);
            if (currentUser.id().equals(noteLike.getUserId())) {
                likedNoteIds.add(noteLike.getNoteId());
            }
        }

        List<NoteSummaryVO> records = notes.stream()
                .map(note -> toSummaryVO(
                        note,
                        likeCounts.getOrDefault(note.getId(), 0L),
                        likedNoteIds.contains(note.getId())
                ))
                .toList();

        return new PageResponse<>(
                records,
                pageResult.getTotal(),
                pageResult.getCurrent(),
                pageResult.getSize(),
                pageResult.getPages()
        );
    }

    private NoteSummaryVO toSummaryVO(Note note, long likeCount, boolean likedByMe) {
        return new NoteSummaryVO(
                note.getId(),
                note.getUserId(),
                note.getTitle(),
                note.getBookName(),
                note.getVisibility(),
                note.getStatus(),
                likeCount,
                likedByMe,
                note.getCreatedAt()
        );
    }
    private Note requireNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        }
        return note;
    }

    private void requireManagePermission(Note note, CurrentUser currentUser) {
        if (!canManageNote(note, currentUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该笔记");
        }
    }
    private boolean canManageNote(Note note, CurrentUser currentUser) {
        return note.getUserId().equals(currentUser.id())
                || Integer.valueOf(1).equals(currentUser.role());
    }

    private NoteDetailVO toDetailVO(Note note) {
        List<String> images = loadImageUrls(note.getId());
        List<NoteLike> noteLikes = noteLikeMapper.selectList(
                Wrappers.<NoteLike>lambdaQuery()
                        .eq(NoteLike::getNoteId, note.getId())
        );
        CurrentUser currentUser = UserContext.get();
        long likeCount = noteLikes.size();
        boolean likedByMe = noteLikes.stream()
                .anyMatch(noteLike -> currentUser.id().equals(noteLike.getUserId()));

        return new NoteDetailVO(
                note.getId(),
                note.getUserId(),
                note.getTitle(),
                note.getBookName(),
                note.getContent(),
                note.getVisibility(),
                note.getStatus(),
                likeCount,
                likedByMe,
                note.getCreatedAt(),
                note.getUpdatedAt(),
                images,
                null
        );
    }

    private List<String> loadImageUrls(Long noteId) {
        return noteImageMapper.selectList(
                        Wrappers.<NoteImage>lambdaQuery()
                                .eq(NoteImage::getNoteId, noteId)
                                .orderByAsc(NoteImage::getSortOrder)
                ).stream()
                .map(NoteImage::getImageUrl)
                .toList();
    }
}