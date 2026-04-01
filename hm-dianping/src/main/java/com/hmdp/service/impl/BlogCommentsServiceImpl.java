package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.BlogCommentsVO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Override
    public Result saveBlogComment(BlogComments comment) {
        Long userId = UserHolder.getUser().getId();
        comment.setUserId(userId);
        // 设置默认层级
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        if (comment.getAnswerId() == null) {
            comment.setAnswerId(0L);
        }
        save(comment);
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryBlogComments(Long blogId, Integer current) {
        // 1. 查询博客，目的是知道这篇笔记作者是谁（为了打"作者"标识）
        Blog blog = blogService.getById(blogId);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        Long authorId = blog.getUserId();

        // 2. 分页查询这篇博客下的根评论或所有评论
        Page<BlogComments> pageInfo = query().eq("blog_id", blogId)
                .orderByDesc("create_time")
                .page(new Page<>(current, 20)); // 每页展示20条评论

        List<BlogComments> records = pageInfo.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        // 3. 收集所有相关的 UserID 并批量查询用户信息
        List<Long> userIds = records.stream().map(BlogComments::getUserId).collect(Collectors.toList());
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        // 4. (可选) 如果有嵌套回复，需找出被回复的那个人的名字
        // 为了追求极简，我们这里用简单的单条查询，或者也可以收集 answerId 去查对应 comment 再查用户
        // 简单实现可以直接循环查

        // 5. 拼装 VO
        List<BlogCommentsVO> result = new ArrayList<>();
        for (BlogComments c : records) {
            BlogCommentsVO vo = new BlogCommentsVO();
            vo.setId(c.getId());
            vo.setBlogId(c.getBlogId());
            vo.setParentId(c.getParentId());
            vo.setAnswerId(c.getAnswerId());
            vo.setUserId(c.getUserId());
            vo.setContent(c.getContent());
            vo.setLiked(c.getLiked());
            vo.setCreateTime(c.getCreateTime());

            // 补充用户信息
            User u = userMap.get(c.getUserId());
            if (u != null) {
                vo.setNickname(u.getNickName());
                vo.setIcon(u.getIcon());
            }

            // 判断是否是楼主/作者自己
            vo.setIsAuthor(authorId.equals(c.getUserId()));

            // 附带被回复人的名字
            if (c.getAnswerId() != null && c.getAnswerId() != 0) {
                BlogComments answeredComment = getById(c.getAnswerId());
                if (answeredComment != null) {
                    User ansU = userService.getById(answeredComment.getUserId());
                    if (ansU != null) {
                        vo.setAnswerUserNickname(ansU.getNickName());
                    }
                }
            }
            result.add(vo);
        }

        return Result.ok(result);
    }
}
