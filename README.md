# ⚡ AGENTE TOKIO | TITAN HFT CORE

> **Java 25 Bare Metal High-Frequency Trading Engine**
> *Arquitectura de Arbitraje Espacial Multi-Exchange de Ultra-Baja Latencia.*

## ⛩️ Visión General

**Agente Tokio** no es un bot de trading convencional. Es un sistema de **ciber-guerra financiera** diseñado para operar en la zona de milisegundos. Desplegado estratégicamente en Tokio (Vultr) para minimizar el *Network Jitter* contra los servidores de Asia de Binance, OKX y Bybit.

El sistema ejecuta estrategias de **Arbitraje Espacial Puro** utilizando órdenes **FOK (Fill-or-Kill)**, eliminando el riesgo de *Penny Jumping* y garantizando ejecución atómica: o se gana dinero, o no se opera.

---

## 💎 Tecnología: Por qué Java 25?

A diferencia de los scripts interpretados (Python) o JITs antiguos, Agente Tokio aprovecha las características de vanguardia de **Java 25** para lograr determinismo y velocidad.

### 🚀 1. Virtual Threads (Project Loom) a Escala

El sistema abandona el viejo modelo "Thread-per-Request".

* **Implementación:** Cada Websocket (Binance, OKX, Bybit) y cada cálculo de riesgo corre en un *Virtual Thread* ultraligero.
* **Resultado:** Capacidad de manejar miles de flujos de datos concurrentes con un *overhead* de memoria casi nulo. El *Context Switching* es virtualmente instantáneo.

### 🧩 2. Pattern Matching & Switch Expressions (JEP 441)

La lógica de enrutamiento de órdenes (`ExchangeConnector`) elimina la complejidad ciclomática.

* **Implementación:** El código utiliza **Switch Expressions** con **Pattern Matching** para determinar rutas de ejecución (Fast Lane vs. Heavy Duty) en nanosegundos.
* **Impacto:** Código más limpio, seguro y optimizado por el compilador para saltos directos en memoria.

### 📦 3. Java Records (Datos Inmutables)

* **Implementación:** Todos los DTOs de mercado (`Ticker`, `OrderBook`) son `record`.
* **Impacto:** Elimina el *boilerplate* y, más importante, permite al **Garbage Collector** optimizar la gestión de memoria de objetos de vida corta, reduciendo la presión en el Heap durante ráfagas de volatilidad.

### ⚡ 4. ZGC (Z Garbage Collector) Generational

* **Configuración:** El motor corre sobre ZGC Generacional.
* **Resultado:** Pausas de recolección de basura **consistentemente por debajo de 1ms**, incluso con Heaps de varios Gigabytes. El bot nunca se "congela" durante una oportunidad de mercado.

---

## 🏗️ Arquitectura del Sistema

### El Núcleo: `Titan HFT Core`

El sistema opera bajo una arquitectura de **Doble Carril (Dual Lane Architecture)**:

1. **🏎️ Fast Lane (UDP/Network Speed):**
* Lectura de Precios y *Order Books*.
* Sin reintentos. Si el dato es viejo, se descarta.
* Latencia interna: **< 200µs**.


2. **🛡️ Heavy Duty Lane (Transactional Integrity):**
* Ejecución de Órdenes y Gestión de Saldos.
* Lógica de Reintentos Exponenciales y Manejo de Rate Limits (429).
* Garantía de entrega ACID sobre HTTP/2.



### Conectividad: "Frankenstein" Connector

Un módulo de conectividad unificado que normaliza las peculiaridades de las APIs:

* **OKX:** Parche de seguridad (User-Agent Spoofing + Atomic Timestamping).
* **Bybit:** Gestión de Subcuentas Unificadas (Unified Trading).
* **Binance/Mexc:** Optimización de firma HMAC-SHA256.

---

## 📊 Dashboard de Telemetría (Console UI)

El sistema renderiza su estado en tiempo real directamente en la terminal (Bare Metal Friendly), utilizando códigos ANSI para visualización instantánea de estado.

```text
╔══════════════════╦══════════╦══════════════════════════╗
║ 🏦 ESTADO DEL TESORO (INVENTARIO REAL - MODO RAW)      ║
╠══════════════════╬══════════╬══════════════════════════╣
║ EXCHANGE         ║ ACTIVO   ║ DISPONIBLE               ║
║ BINANCE          ║ USDT     ║ 3,000.00                 ║
║ OKX              ║ PEPE     ║ 250,000,000.00           ║
╚══════════════════╩══════════╩══════════════════════════╝

[15:42:48] 📡 RADAR DE OPORTUNIDADES:
   💡 #1 PEPE/USDT | Spread: 0.35% | Ganancia Neta: $12.50
   👉 Ejecutando FOK en 4ms...

```

---

## 🛠️ Instalación y Despliegue

### Requisitos

* **Java JDK 25** (Oracle o OpenJDK).
* Servidor Linux (Ubuntu 22.04+) en **Tokio (Recomendado)**.
* 4GB RAM mínimo.

### Configuración (.env)

El sistema requiere un archivo `.env` en la raíz (no incluido en el repo por seguridad).

```properties
# --- CREDENTIALS ---
BINANCE_KEY=...
BINANCE_SECRET=...
OKX_KEY=...
OKX_PASSPHRASE=...
# ... (Bybit, Mexc, Kucoin)

# --- STRATEGY SETTINGS ---
# Ganancia mínima neta (0.001 = 0.1%)
PROF_NORM=0.001
# Capital por operación (98% del saldo)
TRADE_SIZE_PCT=0.98
# Activos a cazar
HUNTING_GROUNDS=PEPE,WIF

```

### Ejecución

```bash
./gradlew run

```

---

## ⚠️ Disclaimer

*Este software es una herramienta de trading algorítmico de alto riesgo. El rendimiento pasado no garantiza resultados futuros. El autor no se hace responsable por pérdidas financieras derivadas de la volatilidad del mercado, fallos de API de terceros o latencia de red.*

---

**© 2025-2026 Rafael Diaz **
*From Garage to Asia* 🚀
