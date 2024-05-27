package test;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class MyLibrary {
  static MyLibrary create(String name, int numberOfLegs) {
    return new AutoValue_MyLibrary(name, numberOfLegs);
  }

  abstract String name();
  abstract int numberOfLegs();
}
