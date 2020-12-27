package com.tiji.center.controller;

import com.tiji.center.pojo.*;
import com.tiji.center.service.TaskService;
import com.tiji.center.service.TaskipService;
import com.tiji.center.service.TaskportService;
import entity.PageResult;
import entity.Result;
import entity.StatusCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * taskport控制器层
 *
 * @author 贰拾壹
 */
@RestController
@CrossOrigin
@RequestMapping("/taskport")
public class TaskportController {

    @Autowired
    private TaskportService taskportService;
    @Autowired
    private TaskipService taskipService;
    @Autowired
    private TaskService taskService;

    /**
     * 查询全部数据
     *
     * @return
     */
    @GetMapping
    public Result findAll() {
        return new Result(true, StatusCode.OK, "查询成功", taskportService.findAll());
    }

    /**
     * 根据ID查询
     *
     * @param id ID
     * @return
     */
    @GetMapping(value = "/{id}")
    public Result findById(@PathVariable String id) {
        return new Result(true, StatusCode.OK, "查询成功", taskportService.findById(id));
    }


    /**
     * 分页+多条件查询
     *
     * @param searchMap 查询条件封装
     * @param page      页码
     * @param size      页大小
     * @return 分页结果
     */
    @PostMapping(value = "/search/{page}/{size}")
    public Result findSearch(@RequestBody Map searchMap, @PathVariable int page, @PathVariable int size) {

        //根据任务名称查询端口
        List<String> taskPortIdList = new ArrayList<>();
        if (searchMap.containsKey("taskid")) {
            //ip -> assetportid
            String taskid = (String) searchMap.get("taskid");
            Map<String, String> ipSearchMap = new HashMap<>();
            ipSearchMap.put("taskid", taskid);
            List<Taskip> taskipList = taskipService.findSearch(ipSearchMap);
            taskipList.forEach(taskip -> {
                String taskipId = taskip.getId();
                List<Taskport> taskportList = taskportService.findByTaskipid(taskipId);
                taskportList.forEach(taskport -> {
                    taskPortIdList.add(taskport.getId());
                });

            });
            searchMap.put("id", taskPortIdList);
        }

        System.out.println("taskPortIdList " + taskPortIdList);
        Page<Taskport> pageList = taskportService.findSearch(searchMap, page, size);
        pageList.stream().parallel().forEach(taskport -> {
            String taskipid = taskport.getTaskipid();
            if (!StringUtils.isEmpty(taskipid)) {
                Taskip taskip = taskipService.findById(taskipid);
                if (!Objects.isNull(taskip)) {
                    taskport.setTaskipid(taskip.getIpaddressv4());
                    String taskid = taskip.getTaskid();
                    if (!StringUtils.isEmpty(taskid)) {
                        Task task = taskService.findById(taskid);
                        if (!Objects.isNull(task)) {
                            taskport.setTaskname(task.getName());
                        }
                    }
                }
            }

        });
        return new Result(true, StatusCode.OK, "查询成功", new PageResult<Taskport>(pageList.getTotalElements(), pageList.getContent()));
    }

    /**
     * 根据条件查询
     *
     * @param searchMap
     * @return
     */
    @PostMapping(value = "/search")
    public Result findSearch(@RequestBody Map searchMap) {
        return new Result(true, StatusCode.OK, "查询成功", taskportService.findSearch(searchMap));
    }

    /**
     * 增加
     *
     * @param taskport
     */
    @PostMapping
    public Result add(@RequestBody Taskport taskport) {
        taskportService.add(taskport);
        return new Result(true, StatusCode.OK, "增加成功");
    }

    /**
     * 修改
     *
     * @param taskport
     */
    @PutMapping(value = "/{id}")
    public Result update(@RequestBody Taskport taskport, @PathVariable String id) {
        taskport.setId(id);
        taskportService.update(taskport);
        return new Result(true, StatusCode.OK, "修改成功");
    }

    /**
     * 删除
     *
     * @param id
     */
    @DeleteMapping(value = "/{id}")
    public Result delete(@PathVariable String id) {
        taskportService.deleteById(id);
        return new Result(true, StatusCode.OK, "删除成功");
    }

    /**
     * 根据ids批量删除
     *
     * @param ids
     */
    @PostMapping(value = "/deleteids")
    public Result deleteAllByIds(@RequestBody List<String> ids) {
        taskportService.deleteAllByIds(ids);
        return new Result(true, StatusCode.OK, "删除成功");
    }

}
