# PedalLog 🚴‍♂️⌚

PedalLog é um aplicativo de rastreamento de ciclismo projetado focado em relógios **Wear OS**. Ele atua como um capturador inteligente de dados, desenhado para economizar bateria com recursos modernos como **Ambient Mode** e **Auto-Pause**, além de sincronizar de maneira eficiente o percurso finalizado com o seu smartphone via **Google Play Services Data Layer**.

## 🏗️ Arquitetura do Sistema

A comunicação e a persistência de dados do PedalLog foram desenhadas para contornar as limitações do Wear OS (bateria pequena e conexão instável), compactando a rota antes de enviá-la ao celular.

```mermaid
graph TD
    %% Estilo Global
    classDef watchStyle fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef phoneStyle fill:#f1f8e9,stroke:#33691e,stroke-width:2px;
    classDef syncStyle fill:#fff3e0,stroke:#e65100,stroke-dasharray: 5 5;

    subgraph Watch ["⌚ RELÓGIO (Wear OS) - O CAPTURADOR"]
        direction TB
        GPS[🛰️ GPS: Captura latitude/longitude] --> Service
        
        Service[⚙️ MOTOR DO APP: Filtra dados e controla estados]
        
        UI[🔘 BOTÃO: Start / Pause / Finish] -- Comandos --> Service
        
        Service -- "Se ATIVO: Grava pontos" --> DB[(🗄️ BANCO DE DADOS: Guarda Sessões e Pontos)]
        
        Finish[🏁 CLIQUE LONGO: Finalizar Jornada] --> Manager[📦 COMPRESSOR: Compacta tudo para caber no Bluetooth]
    end

    subgraph Sync ["📡 PONTE DE COMUNICAÇÃO"]
        Manager -- "GZIP + CSV" --> DataLayer((🔄 GOOGLE DATA LAYER))
    end

    subgraph Phone ["📱 TELEMÓVEL (Mobile) - O CÉREBRO"]
        DataLayer --> Receiver[📩 RECEPTOR: Ouve o relógio e recebe o pacote]
        Receiver -- "Descompacta e Salva" --> History[(📚 HISTÓRICO: Banco de dados permanente)]
        History --> Map[🗺️ MAPA: Desenha o percurso e mostra os KM]
    end

    %% Aplicação de Estilos
    class Watch watchStyle;
    class Phone phoneStyle;
    class Sync syncStyle;
```

## 🗄️ Modelagem de Dados (Room DB)

A persistência local no relógio utiliza duas tabelas primárias que funcionam offline e servem como fonte da verdade até que a sincronização seja confirmada:

```mermaid
erDiagram
    PedalSession ||--o{ PedalPoint : possui

    PedalSession {
        int id PK
        string startTime
        string endTime
        float totalDistance
        boolean isPaused
        string syncUuid
    }

    PedalPoint {
        int id PK
        int sessionId FK
        double latitude
        double longitude
        double speed
        double distance
        int timestamp
    }
```

## 🛠️ Funcionalidades Embutidas no Relógio

- **Máquina de Estados de Sessão**: Suporte completo a Start, Pause e Resume de pedaladas.
- **Auto-Pause & Auto-Resume Inteligente**: O GPS detecta quando a bicicleta para (< 0.5 m/s) e entra em modo de economia de energia, alterando a frequência de rastreamento e emitindo *feedback háptico* de vibração.
- **Prevenção contra Burn-in**: O design em *Ambient Mode* desloca levemente os pixels em background a cada minuto e reverte os painéis brilhantes em contornos super contrastantes, poupando bateria e sua tela OLED.
- **Compressão GZIP & CSV**: Para não esbarrar no limite de 100 KB imposto pela Google para pacotes de DataItem enviados por Bluetooth/Wi-Fi, as coordenadas completas de 1 hora de pedal passam de ~160 KB para meros ~30 KB.

## 🚀 Próximos Passos
O núcleo do relógio (O Capturador) já está completamente maduro. A próxima fase envolve estruturar o pacote `app-mobile` que implementará os "Listeners" do *Google Data Layer* para extrair o `UUID` da pedalada e plotar a trilha GZIP em um Google Maps definitivo.
