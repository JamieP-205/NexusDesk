package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/assets")
public class AssetController {
    private final AssetService assets;
    private final UserService users;
    private final Guide guide;
    public AssetController(AssetService assets, UserService users, Guide guide) { this.assets = assets; this.users = users; this.guide = guide; }

    @GetMapping
    String list(Authentication auth, @RequestParam(required=false) Long owner, Model model) {
        var me = users.current(auth);
        model.addAttribute("assets", assets.list(me, owner)); model.addAttribute("owner", owner);
        model.addAttribute("people", me.isStaff() ? users.list() : List.of(me));
        return "assets";
    }

    @GetMapping("/new")
    String newAsset(Authentication auth, Model model) {
        Rules.admin(users.current(auth));
        model.addAttribute("form", Map.of("type", "Laptop"));
        return "asset-new";
    }

    @PostMapping
    String create(Authentication auth, Model model, HttpServletResponse response, @RequestParam(defaultValue="") String type,
                  @RequestParam(defaultValue="") String manufacturer, @RequestParam(name="model", defaultValue="") String modelName,
                  @RequestParam(defaultValue="") String serial, @RequestParam(defaultValue="") String purchaseDate,
                  RedirectAttributes flash) {
        try {
            long id = assets.create(users.current(auth), type, manufacturer, modelName, serial, date(purchaseDate));
            flash.addFlashAttribute("success", "Added AS-" + id + " to the register. It's available to assign.");
            return "redirect:/assets/" + id;
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            modelFor(model, problem, Map.of("type", type, "manufacturer", manufacturer, "model", modelName,
                    "serial", serial, "purchaseDate", purchaseDate));
            return "asset-new";
        }
    }

    @GetMapping("/{id}")
    String detail(Authentication auth, @PathVariable long id, Model model) {
        var me = users.current(auth);
        var asset = assets.get(me, id);
        detailModel(me, id, model);
        model.addAttribute("form", Map.of("type", asset.type(), "manufacturer", asset.manufacturer(), "model", asset.model(),
                "serial", asset.serialNumber(), "purchaseDate", asset.purchaseDate().toString(), "status", asset.status()));
        return "asset-detail";
    }

    private void detailModel(Account me, long id, Model model) {
        model.addAttribute("asset", assets.get(me, id));
        model.addAttribute("people", me.isAdmin() ? users.active() : List.of());
        model.addAttribute("assignments", me.isStaff() ? assets.history(me, id) : List.of());
    }

    @PostMapping("/{id}")
    String edit(Authentication auth, @PathVariable long id, Model model, HttpServletResponse response, @RequestParam int version,
                @RequestParam(defaultValue="") String type, @RequestParam(defaultValue="") String manufacturer,
                @RequestParam(name="model", defaultValue="") String modelName, @RequestParam(defaultValue="") String serial,
                @RequestParam(defaultValue="") String purchaseDate, @RequestParam(defaultValue="") String status,
                RedirectAttributes flash) {
        var me = users.current(auth);
        try {
            boolean changed = assets.edit(me, id, version, type, manufacturer, modelName, serial, date(purchaseDate), status);
            if (changed) flash.addFlashAttribute("success", "Equipment details saved.");
            else flash.addFlashAttribute("info", "Nothing changed, so there was nothing to save.");
            return "redirect:/assets/" + id;
        } catch (ValidationProblem problem) {
            response.setStatus(400);
            model.addAttribute("errors", problem.fields());
        } catch (Problem problem) {
            if (!problem.isConflict()) throw problem;
            response.setStatus(409);
            model.addAttribute("error", problem.getMessage() + " The page now shows the latest details; your edits are still in the form.");
        }
        detailModel(me, id, model);
        model.addAttribute("form", Map.of("type", type, "manufacturer", manufacturer, "model", modelName,
                "serial", serial, "purchaseDate", purchaseDate, "status", status));
        return "asset-detail";
    }

    @PostMapping("/{id}/assign")
    String assign(Authentication auth, @PathVariable long id, @RequestParam(required=false) Long owner, RedirectAttributes flash,
                  HttpServletRequest request) {
        try {
            var me = users.current(auth);
            assets.assign(me, id, owner);
            guide.done(request, me, "equipment");
            flash.addFlashAttribute("success", "Equipment assigned to " + users.find(owner).name() + ".");
        } catch (ValidationProblem problem) {
            flash.addFlashAttribute("errors", problem.fields());
        }
        return "redirect:/assets/" + id;
    }

    @PostMapping("/{id}/return")
    String returnAsset(Authentication auth, @PathVariable long id, RedirectAttributes flash) {
        assets.returnAsset(users.current(auth), id);
        flash.addFlashAttribute("success", "Equipment returned to available stock. The assignment stays in its history.");
        return "redirect:/assets/" + id;
    }

    private static void modelFor(Model model, ValidationProblem problem, Map<String, String> form) {
        model.addAttribute("errors", problem.fields());
        model.addAttribute("form", form);
    }

    /** An unreadable date becomes null, which the service reports as a field error. */
    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.strip()); } catch (DateTimeParseException e) { return null; }
    }
}
