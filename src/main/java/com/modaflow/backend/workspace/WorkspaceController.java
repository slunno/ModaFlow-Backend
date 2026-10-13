package com.modaflow.backend.workspace;

import com.modaflow.backend.security.TenantAccessValidator;
import com.modaflow.backend.security.UserPrincipal;
import com.modaflow.backend.usuario.UsuarioMarcaRoleRepository;
import com.modaflow.backend.usuario.CargoEnum;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/workspace")
public class WorkspaceController {
    private final WorkspaceStore store;
    private final TenantAccessValidator access;
    private final UsuarioMarcaRoleRepository roles;
    public WorkspaceController(WorkspaceStore store,TenantAccessValidator access,UsuarioMarcaRoleRepository roles) { this.store=store; this.access=access; this.roles=roles; }
    @GetMapping("/history")
    public List<WorkspaceStore.Change> history() { return store.history(access.requireTenantForCurrentUser()); }
    @GetMapping("/{kind}")
    public List<WorkspaceStore.Document> list(@PathVariable String kind) { return store.list(access.requireTenantForCurrentUser(),kind); }
    @PutMapping("/{kind}/{id}")
    public WorkspaceStore.Document save(@PathVariable String kind,@PathVariable String id,@RequestBody WorkspaceStore.Write data,@AuthenticationPrincipal UserPrincipal user) {
        long brand=access.requireTenantForCurrentUser();
        requireWrite(brand,user);
        return store.save(brand,kind,id,data,user.getNome());
    }
    @GetMapping("/products/{document}/attachments")
    public List<WorkspaceStore.Attachment> attachments(@PathVariable String document) { return store.attachments(access.requireTenantForCurrentUser(),document); }
    @GetMapping("/products/{document}/attachments/{id}")
    public ResponseEntity<byte[]> attachment(@PathVariable String document,@PathVariable String id) {
        byte[] data=store.attachmentBytes(access.requireTenantForCurrentUser(),document,id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,"attachment").header("X-Content-Type-Options","nosniff").body(data);
    }
    @PutMapping(value="/products/{document}/attachments/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceStore.Attachment upload(@PathVariable String document,@PathVariable String id,@RequestParam String category,@RequestParam MultipartFile file,@AuthenticationPrincipal UserPrincipal user) throws java.io.IOException {
        long brand=access.requireTenantForCurrentUser(); requireWrite(brand,user);
        return store.putAttachment(brand,document,id,category,file.getOriginalFilename(),file.getContentType()==null?"application/octet-stream":file.getContentType(),file.getBytes(),user.getNome());
    }
    @DeleteMapping("/products/{document}/attachments/{id}")
    public void remove(@PathVariable String document,@PathVariable String id,@AuthenticationPrincipal UserPrincipal user) {
        long brand=access.requireTenantForCurrentUser(); requireWrite(brand,user); store.removeAttachment(brand,document,id,user.getNome());
    }
    private void requireWrite(long brand,UserPrincipal user) {
        if(user==null || roles.findByUsuarioId(user.getId()).stream().noneMatch(r -> r.getMarca().getId().equals(brand) && r.getCargo()!=null && r.getCargo()!=CargoEnum.VISUALIZADOR))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Seu perfil permite apenas consulta nesta marca.");
    }
}
