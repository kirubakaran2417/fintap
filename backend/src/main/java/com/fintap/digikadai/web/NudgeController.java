package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.service.NudgeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nudge")
public class NudgeController {

    private final NudgeService nudgeService;

    public NudgeController(NudgeService nudgeService) {
        this.nudgeService = nudgeService;
    }

    @PostMapping("/whatsapp")
    public Map<String, Object> whatsappNudge(
            @ModelAttribute Merchant merchant,
            @RequestBody NudgeService.NudgeRequest request
    ) {
        return nudgeService.generateWhatsAppNudge(merchant, request);
    }
}
