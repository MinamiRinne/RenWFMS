package org.sysu.workflow.restful.controller;

import org.springframework.web.bind.annotation.*;
import org.sysu.workflow.restful.service.LaunchProcessService;
import org.sysu.workflow.restful.utility.SerializationUtil;
import org.sysu.workflow.restful.utility.TimestampUtil;
import org.sysu.workflow.restful.dto.ReturnElement;
import org.sysu.workflow.restful.dto.ReturnModel;
import org.sysu.workflow.restful.dto.StatusCode;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Ariana
 * Date  : 2018/1/20
 * Usage : Handle requests from other modules.
 */
@RestController
@RequestMapping("/boengine")
public class EngineController {
    /**
     * read xml document from database according to the file name, and then go it.
     * @param pid process id
     * @param roid root BO id
     * @return response package
     */
    @RequestMapping(value = "/launch", produces = {"application/json", "application/xml"})
    @ResponseBody
    @Transactional
    public ReturnModel LaunchProcess(@RequestParam(value = "pid", required = false) String pid,
                               @RequestParam(value = "roid", required = false) String roid) {
        ReturnModel rnModel = new ReturnModel();
        try {
            // miss params
            ArrayList<String> missingParams = new ArrayList<String>();
            if (pid == null) missingParams.add("pid");
            if (roid == null) missingParams.add("roid");
            if (missingParams.size() > 0) {
                return ExcepetionHandler.HandleMissingParameters(missingParams);
            }
            // logic
            LaunchProcessService.LaunchProcess(pid, roid);
            // return
            rnModel.setCode(StatusCode.OK);
            rnModel.setRs(TimestampUtil.GetTimeStamp() + " 0");
            ReturnElement returnElement = new ReturnElement();
            returnElement.setData("LaunchProcess");
            rnModel.setReturnElement(returnElement);
        } catch (Exception e) {
            return ExcepetionHandler.HandleException(e.getClass().getName());
        }
        return rnModel;
    }

    /**
     * Serialized pre-stored BO XML text and return the involved BO list.
     * @param boidlist BOs to be serialized, separated by `,`
     * @return response package
     */
    @RequestMapping(value = "/serializeBO", produces = {"application/json", "application/xml"})
    @ResponseBody
    @Transactional
    public ReturnModel SerializeBO(@RequestParam(value = "boidlist", required = false) String boidlist) {
        ReturnModel rnModel = new ReturnModel();
        try {
            // miss params
            ArrayList<String> missingParams = new ArrayList<String>();
            if (boidlist == null) missingParams.add("boidlist");
            if (missingParams.size() > 0) {
                return ExcepetionHandler.HandleMissingParameters(missingParams);
            }
            // logic
            String jsonify = SerializationUtil.JsonSerialization(LaunchProcessService.SerializeBO(boidlist), "");
            // return
            rnModel.setCode(StatusCode.OK);
            rnModel.setRs(TimestampUtil.GetTimeStamp() + " 0");
            ReturnElement returnElement = new ReturnElement();
            returnElement.setData(jsonify);
            rnModel.setReturnElement(returnElement);
        } catch (Exception e) {
            return ExcepetionHandler.HandleException(e.getClass().getName());
        }
        return rnModel;
    }
}
