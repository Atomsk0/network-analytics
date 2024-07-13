const app = new Vue({
  el: '#app',
  data: {
    trafficData: [],
    anomalyData: [],
  },
  computed: {
    trafficChartData() {
      // Process trafficData for chart
      // This is a placeholder - you'll need to implement this based on your charting library
      return {
        labels: this.trafficData.map(d => new Date(d.timestamp).toLocaleTimeString()),
        datasets: [{
          label: 'Network Traffic (bytes)',
          data: this.trafficData.map(d => d.bytes),
          borderColor: 'blue',
          fill: true
        }]
      };
    },
    anomalyChartData() {
      // Process anomalyData for chart
      // This is a placeholder - you'll need to implement this based on your charting library
      return {
        labels: this.anomalyData.map(d => new Date(d.timestamp).toLocaleTimeString()),
        datasets: [{
          label: 'Anomalies',
          data: this.anomalyData.map(d => d.severity === 'HIGH' ? 1 : 0.5),
          backgroundColor: 'red',
          type: 'bar'
        }]
      };
    },
  },
  methods: {
    async fetchData() {
      try {
        const trafficResponse = await fetch('/api/traffic');
        this.trafficData = await trafficResponse.json();

        const anomalyResponse = await fetch('/api/anomalies');
        this.anomalyData = await anomalyResponse.json();
      } catch (error) {
        console.error('Error fetching data:', error);
      }
    },
  },
  mounted() {
    this.fetchData();
    setInterval(this.fetchData, 5000);
  },
});