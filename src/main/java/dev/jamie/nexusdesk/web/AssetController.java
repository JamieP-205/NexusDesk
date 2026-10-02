package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
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
    public AssetController(AssetService assets, UserService users) { this.assets = assets; this.users = users; }
    @GetMapping
    String list(Authentication auth, @RequestParam(required=false) Long owner, Model model) {
        var me = users.current(auth);
        model.addAttribute("assets", assets.list(me, owner)); model.addAttribute("owner", owner);
        model.addAttribute("people", me.isStaff() ? users.list() : java.util.List.of(me));
        return "assets";
    }
    @GetMapping("/new")
    String newAsset(Authentication auth) { Rules.admin(users.current(auth)); return "asset-new"; }
    @PostMapping
    String create(Authentication auth, @RequestParam String type, @RequestParam String manufacturer, @RequestParam String model,
                  @RequestParam String serial, @RequestParam LocalDate purchaseDate, RedirectAttributes flash) {
        assets.create(users.current(auth), type, manufacturer, model, serial, purchaseDate);
        flash.addFlashAttribute("success", "Asset added to the inventory."); return "redirect:/assets";
    }
    @GetMapping("/{id}")
    String detail(Authentication auth, @PathVariable long id, Model model) {
        var me = users.current(auth);
        model.addAttribute("asset", assets.get(me, id));
        model.addAttribute("people", me.isAdmin() ? users.active() : java.util.List.of());
        model.addAttribute("assignments", me.isStaff() ? assets.history(me, id) : java.util.List.of());
        return "asset-detail";
    }
    @PostMapping("/{id}")
    String edit(Authentication auth, @PathVariable long id, @RequestParam int version, @RequestParam String type,
                @RequestParam String manufacturer, @RequestParam String model, @RequestParam String serial,
                @RequestParam LocalDate purchaseDate, @RequestParam String status, RedirectAttributes flash) {
        assets.edit(users.current(auth), id, version, type, manufacturer, model, serial, purchaseDate, status);
        flash.addFlashAttribute("success", "Asset updated."); return "redirect:/assets/" + id;
    }
    @PostMapping("/{id}/assign")
    String assign(Authentication auth, @PathVariable long id, @RequestParam long owner, RedirectAttributes flash) {
        assets.assign(users.current(auth), id, owner);
        flash.addFlashAttribute("success", "Asset assigned."); return "redirect:/assets/" + id;
    }
    @PostMapping("/{id}/return")
    String returnAsset(Authentication auth, @PathVariable long id, RedirectAttributes flash) {
        assets.returnAsset(users.current(auth), id);
        flash.addFlashAttribute("success", "Asset returned to available stock."); return "redirect:/assets/" + id;
    }
}
