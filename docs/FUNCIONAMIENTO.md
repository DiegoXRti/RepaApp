# Funcionamiento de Repartidor Rutas

## 1. Registrar un pedido

El repartidor puede abrir el formulario desde el botón **Agregar** e introducir nombre, teléfono, dirección, notas, precio y forma de pago. También puede compartir un texto con coordenadas hacia la app: si el texto contiene una pareja latitud/longitud válida, el formulario la reconoce. Si solo se escribe una dirección, la app intenta geocodificarla antes de guardar.

El formulario permite elegir una imagen del dispositivo o tomar una foto. La opción **Escanear ticket** intenta extraer nombre, teléfono, precio, dirección y notas mediante reconocimiento de texto. El resultado debe revisarse antes de guardar, porque el reconocimiento y sus reglas de extracción pueden equivocarse.

## 2. Consultar y editar pedidos

La pantalla principal muestra los pedidos pendientes. Cada tarjeta presenta los datos de la entrega y permite abrir el marcador telefónico, iniciar navegación o marcar el pedido como entregado. Al tocar la tarjeta se abre la edición. Un pedido sin coordenadas no puede incluirse en la optimización de ruta.

## 3. Optimizar el orden de visitas

Con permiso de ubicación, la app toma la última posición disponible del dispositivo y ordena los pedidos que tienen coordenadas. Primero aplica una selección de paradas cercanas con una pequeña prioridad por tiempo de espera; después prueba intercambios 2-opt para reducir la distancia geométrica total.

Esta sugerencia usa distancias en línea recta. **El orden puede diferir del mejor recorrido por carretera**. El repartidor debe revisar restricciones reales como tráfico, horarios y accesos antes de salir.

## 4. Completar entregas y cobros

Al confirmar una entrega puede registrarse una propina. El pedido sale de la lista de pendientes y se puede consultar en la sección de cobros por entregar. La pantalla principal y las estadísticas muestran acumulados de propinas.

## 5. Reutilizar clientes

La sección **Mis Clientes** permite buscar perfiles, abrirlos, editar sus datos y crear un pedido a partir de un perfil existente. También ofrece importación y exportación de clientes en JSON. Estos archivos pueden contener información personal; no deben subirse al repositorio público.

## Alcance de la demostración

Las funciones descritas están implementadas en esta versión. La compilación y las pruebas unitarias pasaron; el flujo completo aún requiere validación en dispositivo antes de declararse listo para producción.

