package com.example.noteworkshop.service;
import com.example.noteworkshop.dto.UpdateProfileRequest;
import com.example.noteworkshop.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.vo.UserProfileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public UserService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            FileStorageService fileStorageService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public UserProfileVO register(RegisterRequest request) {
        String username = request.username().trim();

        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setRole(0);
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User savedUser = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );
        if (savedUser == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "注册后未找到用户");
        }

        log.info("User registered, id={}, username={}", savedUser.getId(), savedUser.getUsername());

        return new UserProfileVO(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getNickname(),
                savedUser.getAvatarUrl(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getCreatedAt()
        );
    }
    public UserProfileVO getCurrentProfile() {
        return toProfileVO(requireCurrentUser());
    }

    @Transactional
    public UserProfileVO updateCurrentProfile(UpdateProfileRequest request) {
        User user = requireCurrentUser();
        String nickname = request.nickname().trim();

        userMapper.update(
                null,
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, user.getId())
                        .set(User::getNickname, nickname)
        );

        User updatedUser = userMapper.selectById(user.getId());
        if (updatedUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        log.info("User profile updated, id={}", updatedUser.getId());
        return toProfileVO(updatedUser);
    }

    private User requireCurrentUser() {
        Long userId = UserContext.get().id();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserProfileVO toProfileVO(User user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
    @Transactional
    public UserProfileVO updateCurrentAvatar(MultipartFile file){
        User user = requireCurrentUser();

        String avatarUrl = fileStorageService.storeAvatar(user.getId(), file);

        userMapper.update(
                null,
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, user.getId())
                        .set(User::getAvatarUrl, avatarUrl)
        );

        User updatedUser = userMapper.selectById(user.getId());
        if (updatedUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        log.info("User avatar updated, id={}", updatedUser.getId());
        return toProfileVO(updatedUser);

    }
}