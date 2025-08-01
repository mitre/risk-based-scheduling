<template>
      <nav>
      <router-link to="/">Home</router-link> |
      <router-link :to="{ name: 'About' }">About</router-link>  
      <router-link :to="{ name: 'Experiments' }">Experiments</router-link>
      <router-link :to="{ name: 'Schedules' }">Schedules</router-link>    
      <router-link :to="{ name: 'CaseFiles' }">Case Files</router-link>    
    </nav>
  <router-view />
</template>

<script setup>
  //
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

nav {
  padding: 30px;
}

nav a {
  font-weight: bold;
  color: #2c3e50;
  text-decoration: none;
  padding: 10px;
  border-radius: 4px;
}

nav a.router-link-exact-active {
  color: white;
  background: #42b983;
}

button {
  margin: 0 10px;
  padding: 10px;
  border: none;
  border-radius: 4px;
}
</style>