#!/bin/sh

# Set NODE_ENV to production for React production builds
export NODE_ENV=production

npm i
rm -rf resource/public
mkdir -p resource/public
cp -r resource/assets/* resource/public
# Build CSS (Tailwind via Vite) then JS (ClojureScript via shadow-cljs)
npx vite build
lein with-profile +ui run -m shadow.cljs.devtools.cli release :frontend
