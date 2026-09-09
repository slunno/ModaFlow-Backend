# ModaFlow PLM — Backend API (Java + Spring Boot)

API RESTful Backend do **ModaFlow PLM (AKR BRANDS)** desenvolvida em **Java 21** e **Spring Boot 3**, projetada para substituir os dados mocks do frontend por persistência em banco de dados relacional (PostgreSQL), autenticação JWT e isolamento multi-tenant por marca comercial (King & Joe, K&J Black, King & Joe Play).

---

## 🚀 Tecnologias Utilizadas

- **Java 21** (LTS)
- **Spring Boot 3.2.3**
  - **Spring Data JPA** (Hibernate)
  - **Spring Security** (Autenticação Stateless JWT)
  - **Spring Validation**
- **PostgreSQL** (Banco de dados relacional)
- **Lombok** (Redução de boilerplate code)
- **MapStruct** (Mapeamento performático DTO <-> Entity)
- **JJWT 0.12.5** (Gerenciamento e validação de tokens JWT)
- **SpringDoc OpenAPI 3.0** (Documentação Swagger UI)

---

## 🏛️ Arquitetura e Camadas do Projeto

O projeto segue a arquitetura clássica em camadas recomendada para ecossistemas Spring Boot corporativos:

```
src/main/java/com/modaflow/backend/
├── config/             # Configurações globais (Security, CORS, OpenAPI, JPA)
├── controller/         # RestControllers expondo endpoints HTTP / JSON
├── dto/
│   ├── request/        # Data Transfer Objects para entrada de dados (Payloads)
│   └── response/       # Data Transfer Objects para saída de dados (Responses)
├── entity/             # Mapeamentos O/R JPA do banco de dados (Entidades)
├── enums/              # Enumeradores do domínio (Cargos, Etapas, Status, Tipos)
├── exception/          # Tratamento global de exceções (@RestControllerAdvice)
├── mapper/             # Interfaces de conversão DTO <-> Entity (MapStruct)
├── repository/         # Interfaces Spring Data JPA para acesso ao banco
├── security/           # Filtros JWT, Contexto Multi-Tenant (ThreadLocal) e UserDetails
├── service/            # Interfaces das regras de negócio
│   └── impl/           # Implementações concretas dos serviços
└── util/               # Utilitários globais e parsers de arquivos
```

---

## 🔐 Segurança & Multi-Tenancy por Marca

Como o ModaFlow PLM gerencia múltiplas marcas do grupo AKR BRANDS (*King & Joe, K&J Black, King & Joe Play*), a segurança opera em duas camadas fundamentais:

1. **Autenticação Stateless JWT**:
   - O usuário realiza o login em `POST /api/v1/auth/login` informando e-mail e senha.
   - A API retorna um token JWT contendo `id`, `email` e as marcas permitidas.
   - O cabeçalho `Authorization: Bearer <token>` deve ser enviado em todas as requisições autenticadas.

2. **Isolamento Multi-Tenant (TenantContext)**:
   - Todas as requisições do frontend enviam o cabeçalho HTTP `X-Marca-Id: <id_da_marca>`.
   - O `TenantInterceptorFilter` intercepta a requisição e armazena o `marcaId` no `TenantContext` (*ThreadLocal*).
   - As consultas e operações do banco de dados filtram automaticamente os dados pela marca ativa do contexto.

---

## 📋 Mapeamento de Endpoints da API

| Módulo | Endpoint | Método | Descrição |
|---|---|---|---|
| **Auth** | `/api/v1/auth/login` | `POST` | Autentica e retorna token JWT + perfil |
| **Auth** | `/api/v1/auth/me` | `GET` | Retorna dados do usuário autenticado e suas marcas |
| **Auth** | `/api/v1/auth/logout` | `POST` | Invalida a sessão do usuário |
| **Marcas** | `/api/v1/marcas` | `GET` | Listar todas as marcas da empresa |
| **Marcas** | `/api/v1/marcas/{id}` | `GET` | Buscar marca por ID |
| **Marcas** | `/api/v1/marcas` | `POST` | Cadastrar nova marca |
| **Marcas** | `/api/v1/marcas/{id}` | `PUT` | Editar dados de uma marca |
| **Usuários** | `/api/v1/usuarios` | `GET` | Listar usuários |
| **Usuários** | `/api/v1/usuarios/{id}` | `GET` | Buscar usuário por ID |
| **Usuários** | `/api/v1/usuarios` | `POST` | Cadastrar novo usuário |
| **Usuários** | `/api/v1/usuarios/{id}` | `PUT` | Editar dados do usuário |
| **Usuários** | `/api/v1/usuarios/{id}/acesso` | `PATCH` | Ativar ou bloquear acesso |
| **Insumos** | `/api/v1/insumos?tipo=TECIDO\|AVIAMENTO` | `GET` | Listar insumos paginados filtrados por tipo |
| **Insumos** | `/api/v1/insumos/{id}` | `GET` | Buscar insumo por ID |
| **Insumos** | `/api/v1/insumos` | `POST` | Cadastrar novo insumo |
| **Insumos** | `/api/v1/insumos/{id}` | `PUT` | Editar insumo |
| **Insumos** | `/api/v1/insumos/{id}` | `DELETE` | Excluir insumo |
| **Insumos** | `/api/v1/insumos/importar` | `POST` | Upload em lote de insumos (CSV/Excel) |
| **Coleções** | `/api/v1/colecoes?status=` | `GET` | Listar coleções filtradas por status |
| **Coleções** | `/api/v1/colecoes/{id}` | `GET` | Buscar detalhes da coleção com suas peças |
| **Coleções** | `/api/v1/colecoes` | `POST` | Criar nova coleção |
| **Coleções** | `/api/v1/colecoes/{id}` | `PUT` | Editar coleção |
| **Peças** | `/api/v1/pecas` | `GET` | Listar peças com filtros paginados |
| **Peças** | `/api/v1/pecas/{id}` | `GET` | Detalhes da peça |
| **Peças** | `/api/v1/pecas` | `POST` | Criar peça |
| **Peças** | `/api/v1/pecas/{id}` | `PUT` | Editar peça |
| **Peças** | `/api/v1/pecas/{id}/etapa` | `PATCH` | Mover peça para uma nova etapa do fluxo |
| **Dashboard** | `/api/v1/dashboard/metrics` | `GET` | Métricas agregadas por etapa |
| **Dashboard** | `/api/v1/dashboard/graficos` | `GET` | Dados agregados para gráficos de BI |
| **Kanban** | `/api/v1/kanban` | `GET` | Peças agrupadas para o quadro de 23 etapas |
| **Kanban** | `/api/v1/kanban/pecas/{id}/etapa` | `PATCH` | Atualizar etapa via drag-and-drop |

---

## 🛠️ Como Executar o Projeto Localmente

### Pré-requisitos
- JDK 21 instalado e configurado na variável `JAVA_HOME`.
- Apache Maven 3.9+ (ou utilizar `./mvnw`).
- PostgreSQL rodando localmente na porta `5432` com a database `modaflow_db`.

### Passos
1. Clone o repositório:
   ```bash
   git clone https://github.com/slunno/ModaFlow-Backend.git
   cd ModaFlow-Backend
   ```

2. Compile o projeto e baixe as dependências:
   ```bash
   mvn clean install
   ```

3. Execute a aplicação Spring Boot:
   ```bash
   mvn spring-boot:run
   ```

4. Acesse a documentação interativa Swagger UI:
   - URL: `http://localhost:8080/api/v1/swagger-ui.html`
