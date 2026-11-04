# Resetting test settings

Close Minecraft or stop the server. Extract/download this whole directory, run `Reset-Defaults.cmd`, and enter the instance or server root folder containing `mods`.

The script backs up existing settings before replacing them with `incursions-server.toml`. It resets root `config`, `defaultconfigs`, and existing standard world `serverconfig` files for every saved world in that instance. Dedicated servers using a standard `level-name` are supported. External or nonstandard world paths need their serverconfig copied manually. The script does not install the JAR or change datapacks.

On other operating systems, back up the existing file and copy the supplied TOML to the corresponding location manually.

The Windows script was reviewed but not executed on Windows during this release preparation.
