set -eu
mkdir -p build
javac -encoding UTF-8 --release 21 -cp 'lib/*' -d build src/barbearia/*.java
jar --create --file fl-barbearia.jar --main-class barbearia.Servidor --manifest manifesto.txt -C build .
