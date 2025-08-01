/<template>
  <h1>GRACE Risk-Based Scheduling Tool</h1>
  <v-div class="about-page">
    <v-container>
      <v-row>
        <v-col
          class="d-flex align-center"
          cols="12"
          sm="12"
        >
          <p class="text-justify">
            This tool was created by The MITRE Corporation with the pediatric cardiac catheterization lab 
            at Boston Children's Hospital (BCH) as a tool to enable hospital decision-makers to evaluate scheduling policies
            before implementing them. The aim of the work is to leverage predictive modeling and discrete event simulation to
              validate human-interpretable scheduling heuristics that decrease system-level risk, increase system-level 
              efficiency, and are easily integrated into existing scheduler workflows. This framework can be used not only to 
              evaluate the impact of scheduling protocol changes, but it can also capture the impact of an altered lab 
              configuration, changes in case arrival rates, and changes in case population complexity. Although the framework is 
              being prototyped at BCH, the team is developing a generalized tool that can be utilized in complex care 
              environments beyond the BCH pediatric cardiac catheterization lab. 
          </p>
        </v-col>
      </v-row>
      <p>This tool has three main components that can be accessed through this user interface.  </p>
    </v-container>
    <div class="architecture">
      <h2>Scheduling Tool Framework</h2>
      <div class="ma-4">
        <v-img class="architecture_img" src="../assets/architecture.png" cover></v-img>
      </div>
    </div>
    <v-container>
        <h3 class="text-left">Case Files</h3>
        <v-row>
          <v-col
            class="d-flex align-center"
            cols="12"
            sm="12"
          >
            <p class="text-left">
              The first step is to upload patient case files through the Case Files tab. JSON files can be uploaded for both 
              elective and add-on cases. These cases can be synthetic patient case data or historic patient cases. Elective 
              cases are patient cases that are scheduled in advance through the scheduler and add-on cases arrive during the 
              simulation and must be added onto the existing schedule.  
            </p>
          </v-col>
        </v-row>
        <h3 class="text-left">Schedules</h3>
        <v-row>
          <v-col
            class="d-flex align-center"
            cols="12"
            sm="12"
          >
            <p class="text-left">
              Once there is a case file uploaded the cases can be added to a schedule in this tab. A schedule is created for 
              the elective cases in the selected case file. Each schedule can be varied by changing scheduling parameters, 
              such as number of schedule iterations, the schedule dates, rate of patient arrivals, scheduling lead time, and 
              scheduling heuristic. To allow adaptability for multiple care environments, scheduling rules and lab 
              configurations can be uploaded as JSON files. Schedule results can be viewed by clicking the view button in 
              line with the schedule of interest. The results show the average daily complexity of cases. 
            </p>
          </v-col>
        </v-row>
        <h3 class="text-left">Experiments</h3>
        <v-row>
          <v-col
            class="d-flex align-center"
            cols="12"
            sm="12"
          >
            <p class="text-left">
              The created schedules can then be run through a simulation to test the performance of the schedule under 
              stochastic conditions for case length, adverse events, and add-on case arrivals. In the Experiments tab, 
              simulations can be run by specifying the schedule to be simulated and the number of iterations. f desired, 
              the number of extra simulation days beyond the schedule end date and the start seed can also be specified. 
              Additional simulation settings such as lab configuration and risk thresholds can be adjusted through the simulation 
              configuration JSON, which can be uploaded to the tool. Outputs for each simulation can be viewed by clicking 
              the results box next to the simulation of interest.  
            </p>
          </v-col>
        </v-row>
      </v-container>
      <v-container>
      <v-row>
        <v-col
          class="d-flex align-center"
          cols="12"
          sm="12"
        >
          <p class="docs">
            More in-depth documentation can be found in the <a :href=repo_url>GitHub repository docs</a>. 
            Contact: David Slater (<a>dslater@mitre.org</a>), Madi Ramsey (<a>mramsey@mitre.org</a>).
          </p>
        </v-col>
      </v-row>
    </v-container>
  </v-div>
</template>

<script>
export default {
    name: 'About',
    setup() {

      const repo_url = 'https://github.com/mitre/risk-based-scheduling'

      return { repo_url }
    }
}
</script>

<style>

.about-page p {
  padding-top: 1em;
  padding-bottom: 1em;
}

.about-page h2 {
  padding-top: 2em;
  padding-bottom: 1em;
}

.about-page h3 {
  padding-top: 2em;
  padding-bottom: 0em;
}

.about-page v-spacer {
  padding-bottom: 3em;
}

.architecture_img {
  display: block;
  margin-left: 25%;
  width: 50%;
}

.docs a {
    font-weight: bold;
    color: #42b983;
    text-decoration: none;
    padding: 2px;
    border-radius: 4px;
}

</style>