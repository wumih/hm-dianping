package com.hmdp.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BlogCommentsVO {
    private Long id;
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private Long userId;
    private String content;
    private Integer liked;
    private Boolean isLike;
    private LocalDateTime createTime;

    // 评论人信息
    private String nickname;
    private String icon;

    // 权限与显示相关
    private Boolean isAuthor; // 是否为文章的作者自评

    // 若这是回复(answerId != 0)，展示被回复那个人的昵称
    private String answerUserNickname;
}
