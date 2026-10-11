# Verificación

## Comandos

Desde la raíz del proyecto, con JDK 17 y Android SDK configurados:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:connectedDebugAndroidTest
```

El último comando necesita un emulador o dispositivo conectado. Compilar las pruebas instrumentadas no equivale a ejecutarlas.

## Escenarios manuales prioritarios

1. Crear y editar un pedido; al cambiar la dirección, comprobar que la navegación no usa las coordenadas antiguas.
2. Conceder el permiso de cámara por primera vez y verificar que la captura continúa sin volver a tocar el botón.
3. Abrir y cerrar varias veces la búsqueda de clientes; confirmar que no aparecen resultados antiguos ni se acumulan observadores.
4. Compartir el log con otra app y comprobar que se abre el selector de envío.
5. Crear un pedido un día y entregarlo al siguiente; comprobar que la propina aparece en el día de la entrega.
6. Instalar una versión previa con datos ficticios y actualizarla a la versión 9; confirmar que pedidos y clientes siguen presentes.
7. Probar un pedido sin coordenadas, uno con coordenadas compartidas y uno con dirección geocodificada.

## Pruebas existentes

- `OrderCoordinatePolicyTest`: tratamiento de coordenadas al editar dirección.
- `AppDatabaseMigrationTest`: preservación de datos en migraciones.
- `OrderDaoTipDateTest`: propinas por fecha de entrega.
- `AppLoggerFileProviderTest`: URI compartible del log.

Para una distribución instalable, registrar fecha, dispositivo o emulador, versión Android y resultado de cada escenario.

