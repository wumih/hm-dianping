package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result queryMyBlog(Integer current);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);

    /**
     * 修改博客（含权限校验，只有作者本人可改）
     * @param blog 包含 id 及要更新字段的博客对象
     * @return Result
     */
    Result updateBlog(Blog blog);

    /**
     * 删除博客（含权限校验与 Redis 缓存清理）
     * @param id 博客id
     * @return Result
     */
    Result deleteBlog(Long id);
}
