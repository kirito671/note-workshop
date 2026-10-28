package com.example.noteworkshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.noteworkshop.entity.Note;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@SuppressWarnings("SqlResolve")
public interface NoteMapper extends BaseMapper<Note> {

    @Select("""
            SELECT n.*
            FROM note n
            INNER JOIN note_like nl ON nl.note_id = n.id
            WHERE nl.user_id = #{userId}
              AND n.visibility = 1
              AND n.status = 1
            ORDER BY nl.created_at DESC, nl.id DESC
            """)
    Page<Note> selectLikedNotes(
            Page<Note> page,
            @Param("userId") Long userId
    );

    @Select("""
            SELECT n.*
            FROM note n
            LEFT JOIN (
                SELECT note_id, COUNT(*) AS like_count
                FROM note_like
                GROUP BY note_id
            ) stats ON stats.note_id = n.id
            WHERE n.visibility = 1
              AND n.status = 1
            ORDER BY COALESCE(stats.like_count, 0) DESC,
                     n.created_at DESC,
                     n.id DESC
            """)
    Page<Note> selectRankingNotes(Page<Note> page);
}