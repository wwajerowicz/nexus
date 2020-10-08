package ch.epfl.bluebrain.nexus.delta.service.plugin

import java.net.URL

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.{Failure, Success, Try}

/**
  * Wrapper around URLClassloader that tries to load the classes from the JAR first.
  *
  * Inspired by https://github.com/pf4j/pf4j/blob/master/pf4j/src/main/java/org/pf4j/PluginClassLoader.java
  */
class PluginClassLoader(url: URL, parent: ClassLoader) extends URLClassLoader(Seq(url), parent) {

  /**
    * Loads the class with the specified class name. It first tries to find the class in the Jar specified as `url`
    * and if it cannot be found, uses parent class loader.
    * It loads Java classes using the system classloader and delegates loading of all `scala.` classes to the parent
    * classloader.
    *
    * @param className The binary name of the class
    * @return           The resulting [[Class]] object
    */
  override def loadClass(className: String): Class[_] =
    getClassLoadingLock(className).synchronized {
      className match {
        case systemClass if systemClass.startsWith("java.") => findSystemClass(systemClass)
        case scalaClass if scalaClass.startsWith("scala.")  => super.loadClass(scalaClass)
        case _                                              =>
          Option(findLoadedClass(className)) match {
            case Some(alreadyLoaded) => alreadyLoaded
            case None                =>
              Try { findClass(className) } match {
                case Success(found)                     => found
                case Failure(_: ClassNotFoundException) => super.loadClass(className)
                case Failure(ex)                        => throw ex
              }
          }
      }
    }

  /**
    * Finds the resource with the given name. Returns the resource from the plugin's classpath, if exists
    * Otherwise, uses parent classloader to load the resource
    *
    * @param name the name of the resource.
    * @return the URL to the resource, [[null]] if the resource was not found.
    */
  override def getResource(name: String): URL =
    Option(findResource(name)) match {
      case Some(res) => res
      case None      => super.getResource(name)
    }
}
