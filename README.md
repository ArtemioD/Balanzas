# 🔬 Simulador de Balanza SERIAL para Sarga Digital

## 📋 Descripción

Esta aplicación Android simula una balanza Bluetooth compatible con Sarga Digital. Permite probar el sistema de pesaje sin necesidad de hardware físico.

## ✨ Características

- **Nombre del dispositivo**: `SERIAL_SIMULADOR_001` (detectado automáticamente por Sarga)
- **Protocolo**: Bluetooth SPP (Serial Port Profile)
- **UUID**: `00001101-0000-1000-8000-00805F9B34FB`
- **Formato de trama**: Exactamente compatible con `PesadoraBluetooth.kt`

## 🔧 Formato de Trama

```
[DESCRIPCION_15_CHARS][PESO_8_CHARS][CR]
```

- **Descripción**: 15 caracteres, relleno con espacios al final
- **Peso**: 8 caracteres, relleno con espacios al inicio
- **Delimitador**: ASCII 13 (Carriage Return)

### Ejemplo:
```
BOVINO ADULTO  450.5000\r
```

## 📱 Cómo Usar

### 1. Instalación
```bash
cd BalanzaSimulador
./gradlew installDebug
```

### 2. Configuración
1. Abre la app "Balanza Simulador"
2. Concede permisos Bluetooth
3. Activa el servidor Bluetooth con el switch

### 3. Conexión desde Sarga Digital
1. Ve al fragment de pesada en Sarga Digital
2. Busca dispositivos Bluetooth
3. Selecciona "SERIAL_SIMULADOR_001"
4. Conéctate

### 4. Simular Pesajes
1. Ingresa descripción del animal (máx. 15 chars)
2. Ingresa peso en kg (máx. 8 chars)
3. Toca "Enviar Peso a Sarga Digital"

## 🧪 Casos de Prueba

### Valores de Prueba Recomendados:
- **Bovino adulto**: `BOVINO ADULTO` / `450.75`
- **Ternera**: `TERNERA` / `120.5`
- **Ovino**: `OVINO` / `45.25`
- **Peso máximo**: `ANIMAL GRANDE` / `999.9999`

### Estados a Probar:
1. **Conexión exitosa**: Verificar que Sarga detecta y conecta
2. **Envío de datos**: Confirmar que los pesos llegan correctamente
3. **Desconexión**: Probar reconexión automática
4. **Errores de formato**: Valores fuera de rango

## 🔍 Logs de Debug

La app genera logs detallados con tag `BalanzaSimulador`:

```bash
adb logcat -s BalanzaSimulador
```

## ⚙️ Configuración Técnica

### Permisos Requeridos:
- `BLUETOOTH` / `BLUETOOTH_ADMIN` (Android < 12)
- `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (para descobrimiento)

### Especificaciones:
- **Min SDK**: 31 (Android 12)
- **Target SDK**: 34 (Android 14)
- **UUID SPP**: Estándar Serial Port Profile
- **Timeout**: Sin timeout (conexión persistente)

## 🐛 Troubleshooting

### Problema: Sarga no encuentra el simulador
**Solución**: 
1. Verificar que el servidor esté activo (switch ON)
2. Comprobar permisos Bluetooth
3. Asegurar que el nombre sea exactamente `SERIAL_SIMULADOR_001`

### Problema: Conexión se pierde
**Solución**:
1. Mantener la app simulador en primer plano
2. Verificar que no hay ahorro de energía activo
3. Reiniciar el servidor si es necesario

### Problema: Datos no llegan a Sarga
**Solución**:
1. Verificar formato de trama en logs
2. Comprobar que hay conexión activa (LED verde en status)
3. Verificar que el peso tenga formato numérico válido

## 📋 Notas de Desarrollo

- El simulador implementa el protocolo exacto de `PesadoraBluetooth.kt`
- Usa el mismo UUID que las balanzas reales
- Mantiene compatibilidad 100% con el sistema existente
- No requiere modificaciones en Sarga Digital

## 🚀 Próximas Mejoras

- [ ] Simulación de múltiples balanzas simultáneas
- [ ] Presets de animales comunes
- [ ] Log de historial de pesajes
- [ ] Simulación de errores de conexión
- [ ] Interfaz para probar diferentes formatos de trama