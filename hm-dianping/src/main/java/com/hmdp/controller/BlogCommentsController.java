package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/blog/comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    @PostMapping("/save")
    public Result saveBlogComment(@RequestBody BlogComments comment) {
        return blogCommentsService.saveBlogComment(comment);
    }

    @GetMapping("/list/{blogId}")
    public Result queryBlogComments(
            @PathVariable("blogId") Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryBlogComments(blogId, current);
    }
}
