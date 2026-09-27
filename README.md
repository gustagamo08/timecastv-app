# TimeCastV — App Android (celular + Android TV)

App oficial de **timecastv.com**. Un solo APK que funciona en celulares y en
Android TV / Google TV (aparece en la fila de apps del televisor con su banner).

## Qué incluye
- WebView con reproducción automática del canal y pantalla completa real.
- Lanzador de Android TV (`LEANBACK_LAUNCHER`) y banner 320x180.
- Botón **Atrás** del control: primero sale de pantalla completa, luego vuelve
  a la página anterior, y por último pide confirmar para cerrar la app.
- Pantalla "Sin conexión" con botón Reintentar navegable con el control.
- Pantalla siempre encendida mientras se ve el canal.
- Marca el sitio con la clase `android-tv` para los estilos del plugin.

## Compilar
GitHub Actions compila solo en cada cambio a `main` (o con "Run workflow").
El APK sale en la pestaña **Actions → ejecución → Artifacts → TimeCastV-APK**.

Para actualizar la app: sube `versionCode` (+1) y `versionName` en
`app/build.gradle` antes de compilar.
