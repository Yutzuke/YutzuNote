# YutzuNote

Fork de **Notas de /e/OS** (basada en [Nextcloud Notes para Android](https://github.com/nextcloud/notes-android)) pensado para guardar las notas **solo en local**, por ejemplo en la microSD, sin nube.

## Novedades respecto a la app original

- **Notas como archivos `.md` en una carpeta propia** (memoria interna o microSD), con sincronización en los dos sentidos:
  - lo que escribes en la app se guarda en la carpeta segundos después;
  - lo que editas o creas fuera (PC, otro editor) se importa al abrir la app y cada 15 minutos;
  - la categoría es la subcarpeta y el título el nombre del archivo; mover un archivo cambia su categoría.
- **Conflictos sin pérdida**: si una nota cambia en ambos lados, gana la más reciente y la otra se guarda como nota "(conflicto)".
- **Papelera**: las notas borradas se mueven a `.papelera/` en lugar de eliminarse.
- **Copias ZIP** automáticas diarias en `Backups/` con rotación configurable, más copia manual y **restauración** sin duplicados.
- **Importar notas desde carpeta** (`.md`/`.txt`), útil para migrar desde la app Notas (Ajustes → Exportar notas).
- **Exportar una nota a TXT o PDF** y compartirla.
- Paquete `app.yutzunote`: convive con la app Notas del sistema.

Todo se configura en **Ajustes → YutzuNote · carpeta local**.

## Compilar

Necesita JDK 17 y el SDK de Android.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
JAVA_HOME=/ruta/a/jdk-17 ./gradlew assembleRelease
```

La firma se lee de `keystore.properties` (no incluido): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
Tras instalar, concede «Acceso a todos los archivos» (o `adb shell appops set app.yutzunote MANAGE_EXTERNAL_STORAGE allow`).

## Licencia

GPL-3.0, igual que el proyecto original. El código nuevo está en `app/src/main/java/app/yutzunote/`.

---

*README original:*

# Notes
An android client for [Nextcloud Notes App](https://github.com/nextcloud/notes/)  
Notes is forked from [Nextcloud Notes](https://github.com/stefan-niedermann/nextcloud-notes)

## Authors

[Authors](https://gitlab.e.foundation/e/apps/Notes/-/blob/master/AUTHORS)

## Release Notes

Check out the [Release Notes](https://gitlab.e.foundation/e/apps/Notes/-/releases) to find out what changed
in each version of Notes.

## Privacy Policy

[Privacy Policy](https://e.foundation/legal-notice-privacy)  
[Terms of service](https://e.foundation/legal-notice-privacy)

## License

Notes is licensed under the [GNU General Public License v3.0](https://gitlab.e.foundation/e/apps/Notes/-/blob/master/LICENSE)