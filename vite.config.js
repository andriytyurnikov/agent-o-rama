import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(({ mode }) => ({
  plugins: [tailwindcss()],
  build: {
    outDir: "resource/public",
    emptyOutDir: false,
    manifest: "css-manifest.json",
    rollupOptions: {
      input: { main: "src/cljs/com/rpl/agent_o_rama/ui/css/main.css" },
      output: {
        assetFileNames:
          mode === "production" ? "[name].[hash][extname]" : "[name][extname]",
      },
    },
  },
}));
