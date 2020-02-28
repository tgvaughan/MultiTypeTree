# Dockerfile to build container for unit testing

FROM debian:stable

RUN apt-get update
RUN apt-get install -y openjdk-11-jdk
RUN apt-get install -y ant

WORKDIR /root

# The released version of jblas is no longer compatable with Debian
# (and other GNU/Linuxes) due to its being built with an older
# version of fortran.  Thus we have to build it ourselves...

RUN apt-get install -y git
RUN apt-get install -y gfortran
RUN apt-get install -y gcc make ruby
RUN apt-get install -y libopenblas-dev

RUN git clone https://github.com/jblas-project/jblas
WORKDIR /root/jblas
RUN ./configure --build-type=openblas --libpath=/usr/lib/x86_64-linux-gnu/ --lapack-build --static-libs

# We have to patch the ant build script that comes with jblas, as it is
# incompatable with current java versions which lack javah.

RUN echo '142c142,143\n\
<         <javac destdir="${bin}" encoding="utf-8" source="1.6" debug="on" compiler="javac1.5" target="1.6" fork="yes" nowarn="yes">\n\
---\n\
> 		    <!--<javac destdir="${bin}" encoding="utf-8" source="1.6" debug="on" compiler="javac1.5" target="1.6" fork="yes" nowarn="yes">-->\n\
> 	<javac destdir="${bin}" encoding="utf-8" source="1.6" debug="on" compiler="javac1.5" target="1.6" fork="yes" nowarn="yes" nativeHeaderDir="${include}">\n\
146a148\n\
> 	<!--\n\
158a161\n\
> 	-->' >> build.patch
RUN patch build.xml <build.patch

# Now we can build the binaries and the jar
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
RUN make clean all
RUN ant clean jar

#RUN java -jar jblas-1.2.4-SNAPSHOT.jar

WORKDIR /root
ADD . ./

# Now we have to copy the new java library over the old one and place the native
# libraries where they can be found.

RUN cp jblas/jblas-1.2.4-SNAPSHOT.jar lib/jblas-1.2.4.jar
RUN cp jblas/target/classes/lib/static/Linux/amd64/libjblas_arch_flavor.so /usr/lib/x86_64-linux-gnu/
RUN cp jblas/target/classes/lib/static/Linux/amd64/sse3/libjblas.so /usr/lib/x86_64-linux-gnu/
RUN ldconfig

ENTRYPOINT ant test
