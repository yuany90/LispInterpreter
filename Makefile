%.class: %.java
	javac $<
all: LISP.class

clean:
	rm *.class
