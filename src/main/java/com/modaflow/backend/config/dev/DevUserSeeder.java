package com.modaflow.backend.config.dev;

import com.modaflow.backend.usuario.Usuario;
import com.modaflow.backend.usuario.UsuarioRepository;
import com.modaflow.backend.usuario.UsuarioMarcaRole;
import com.modaflow.backend.usuario.UsuarioMarcaRoleRepository;
import com.modaflow.backend.usuario.CargoEnum;
import com.modaflow.backend.marca.Marca;
import com.modaflow.backend.marca.MarcaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@Profile("dev")
public class DevUserSeeder {
    private static final List<GriffeOficial> GRIFFES_OFICIAIS = List.of(
            new GriffeOficial("KING_JOE", "King & Joe", "KJ", "Linha Principal"),
            new GriffeOficial("KJ_BLACK", "K&J Black", "KB", "Linha Premium"),
            new GriffeOficial("KING_JOE_PLAY", "King & Joe Play", "KJP", "Linha Sportwear")
    );

    @Bean
    CommandLineRunner seedDevelopmentUsers(UsuarioRepository usuarios, MarcaRepository marcas,
                                           UsuarioMarcaRoleRepository roles, PasswordEncoder passwordEncoder) {
        return args -> {
            seed(usuarios, passwordEncoder, "Administrador AKR", "admin@akrbrands.com.br", "admin123", "AKR BRANDS", "ADMIN");
            seed(usuarios, passwordEncoder, "Juliano", "juliano@akrbrands.com.br", "modaflow2026", "King & Joe", "GESTOR");
            List<Marca> griffes = GRIFFES_OFICIAIS.stream()
                    .map(griffe -> localizarOuCriar(marcas, griffe))
                    .toList();

            usuarios.findByEmail("admin@akrbrands.com.br").ifPresent(usuario -> {
                griffes.forEach(marca -> {
                    if (!roles.existsByUsuarioIdAndMarcaId(usuario.getId(), marca.getId())) {
                        roles.save(UsuarioMarcaRole.builder().usuario(usuario).marca(marca).cargo(CargoEnum.ADMIN).build());
                    }
                });
            });
            usuarios.findByEmail("juliano@akrbrands.com.br").ifPresent(usuario -> {
                Marca marca = griffes.get(0);
                if (!roles.existsByUsuarioIdAndMarcaId(usuario.getId(), marca.getId())) {
                    roles.save(UsuarioMarcaRole.builder().usuario(usuario).marca(marca).cargo(CargoEnum.GESTOR).build());
                }
            });
        };
    }

    /**
     * Espelha no banco próprio somente as griffes oficiais já autorizadas no Linx.
     * A consulta é idempotente: reaproveita marca existente por código ou por nome.
     */
    private Marca localizarOuCriar(MarcaRepository marcas, GriffeOficial griffe) {
        return marcas.findByCode(griffe.code())
                .or(() -> marcas.findByNomeIgnoreCase(griffe.nome()))
                .orElseGet(() -> marcas.save(Marca.builder()
                        .nome(griffe.nome())
                        .code(griffe.code())
                        .initials(griffe.initials())
                        .badgeTag(griffe.badgeTag())
                        .build()));
    }

    private void seed(UsuarioRepository usuarios, PasswordEncoder encoder, String nome, String email,
                      String senha, String empresa, String cargo) {
        if (!usuarios.existsByEmail(email)) {
            usuarios.save(Usuario.builder().nome(nome).email(email).senha(encoder.encode(senha))
                    .empresa(empresa).cargo(cargo).ativo(true).build());
        }
    }

    private record GriffeOficial(String code, String nome, String initials, String badgeTag) {
    }
}
