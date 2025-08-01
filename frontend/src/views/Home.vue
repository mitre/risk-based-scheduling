<template>
  <h1>GRACE Risk-Based Scheduling Tool</h1>
  <div class="description">
    <p>Welcome!</p>
      <div class="text-justify">
        <p>The GRACE Risk-Based Scheduling Tool is a collaborative project between the MITRE Corporation and Boston Children's Hospital. Together, we have built a scheduling tool that leverages predictive analytics and simulation modeling to evaluate scheduling heuristics before implementing them in real operating departments.</p>
      </div>
      <p>To learn more about the tool and how to use it, click <router-link :to="{ name: 'About' }">here</router-link>.</p>
    </div>
  <div class="description">
    <p>For more detailed usage, refer to the docs  in the <a :href=repo_url>GitHub repository</a>.</p>
  </div>
  <div class="contact">
    <p>Contact: David Slater (<a>dslater@mitre.org</a>), Madi Ramsey (<a>mramsey@mitre.org</a>).</p>
  </div>
</template>

<script>

export default {
    name: 'Home',
    setup() {
        
        const repo_url = 'https://github.com/mitre/risk-based-scheduling'
  
        return { repo_url }
    }
}
</script>

<style>
  .description p {
  display: block;
  margin-top: 1em;
  margin-bottom: 1em;
  margin-left: 10em;
  margin-right: 10em;
}

  .description a {
    font-weight: bold;
    color: #42b983;
    text-decoration: none;
    padding: 2px;
    border-radius: 4px;
}

.contact a {
    font-weight: bold;
    color: #42b983;
    text-decoration: none;
    padding: 2px;
    border-radius: 4px;
}

.contact p {
  padding: 2em;
}

</style>
