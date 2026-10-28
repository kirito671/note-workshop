package com.example.noteworkshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.noteworkshop.entity.User;
import org.apache.ibatis.annotations.Select;

@SuppressWarnings("SqlResolve")
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            SELECT u.*
            FROM user u
            LEFT JOIN user_follow f ON f.following_id = u.id
            WHERE u.role = 0 AND u.status = 1
            GROUP BY u.id, u.username, u.password, u.nickname, u.avatar_url,
                     u.role, u.status, u.created_at, u.updated_at
            ORDER BY COUNT(f.id) DESC, u.id DESC
            """)
    Page<User> selectHotAuthors(Page<User> page);
}